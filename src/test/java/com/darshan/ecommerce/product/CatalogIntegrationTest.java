package com.darshan.ecommerce.product;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.category.Category;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    private String adminToken;
    private Category books;
    private Category electronics;

    @BeforeAll
    void createAdmin() throws Exception {
        api.cleanDatabase();
        api.createAdmin("admin@catalog.example", PASSWORD);
        adminToken = api.login("admin@catalog.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void resetCatalogue() {
        api.cleanCatalogue();
        books = api.createCategory("Books");
        electronics = api.createCategory("Electronics");
    }

    @Test
    void adminCreatesAProductThatThePublicCanRead() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(productBody("BK-DDIA-001", "99.50", books.getId()))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/products/")))
                .andExpect(jsonPath("$.sku").value("BK-DDIA-001"))
                .andExpect(jsonPath("$.price").value(99.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.category.name").value("Books"))
                .andReturn().getResponse().getContentAsString();
        long id = api.read(body).get("id").asLong();

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.category.id").value(books.getId()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createRejectsBadSkuUnknownCategoryAndDuplicateSku() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(productBody("bad sku", "10.00", books.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("sku"));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(productBody("BK-NEW-001", "10.00", 999_999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Category not found with id 999999"));

        api.createProduct("BK-DUP-001", new BigDecimal("5.00"), 1, books);
        mockMvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(productBody("BK-DUP-001", "10.00", books.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("SKU already in use"));
    }

    @Test
    void listReturnsAPageDtoFilteredByEveryParameter() throws Exception {
        Product cheapBook = api.createProduct("BK-CHEAP-001", new BigDecimal("10.00"), 5, books);
        Product dearBook = api.createProduct("BK-DEAR-001", new BigDecimal("90.00"), 0, books);
        Product laptop = api.createProduct("LAP-14-001", new BigDecimal("750.00"), 3, electronics);

        mockMvc.perform(get("/api/v1/products").param("size", "2").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(laptop.getId()))
                .andExpect(jsonPath("$.content[1].id").value(dearBook.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.pageable").doesNotExist());

        mockMvc.perform(get("/api/v1/products").param("categoryId", books.getId().toString()).param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(cheapBook.getId()));

        mockMvc.perform(get("/api/v1/products").param("minPrice", "50").param("maxPrice", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(dearBook.getId()));

        mockMvc.perform(get("/api/v1/products").param("q", "lap-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(laptop.getId()));
    }

    @Test
    void listRejectsOversizedPagesAndUnknownSortProperties() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        mockMvc.perform(get("/api/v1/products").param("sort", "stockQuantity,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported sort property 'stockQuantity'. Allowed: name, price, createdAt"));
    }

    @Test
    void softDeletedProductsVanishFromPublicEndpointsButStayVisibleToAdmins() throws Exception {
        Product product = api.createProduct("BK-GONE-001", new BigDecimal("10.00"), 5, books);
        Product other = api.createProduct("BK-STAY-001", new BigDecimal("10.00"), 5, books);

        mockMvc.perform(delete("/api/v1/admin/products/{id}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found with id " + product.getId()));
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(other.getId()));

        mockMvc.perform(get("/api/v1/admin/products/{id}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(delete("/api/v1/admin/products/{id}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void secondAdminUpdatingWithAnOutdatedVersionGetsConflict() throws Exception {
        Product product = api.createProduct("BK-LOCK-001", new BigDecimal("10.00"), 5, books);
        Map<String, Object> firstAdmin = updateBody(product, "11.00", 0);
        Map<String, Object> secondAdmin = updateBody(product, "12.00", 0);

        mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(firstAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(11.00))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(secondAdmin)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The resource was modified by another request, reload it and retry"))
                .andExpect(jsonPath("$.correlationId").exists());

        JsonNode current = api.read(mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andReturn().getResponse().getContentAsString());
        assertThat(current.get("price").decimalValue()).isEqualByComparingTo("11.00");
        assertThat(current.get("version").asInt()).isEqualTo(1);
    }

    private static Map<String, Object> productBody(String sku, String price, Long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", "Product " + sku);
        body.put("description", "Created through the API");
        body.put("price", price);
        body.put("stockQuantity", 7);
        body.put("categoryId", categoryId);
        return body;
    }

    private static Map<String, Object> updateBody(Product product, String price, int version) {
        Map<String, Object> body = productBody(product.getSku(), price, product.getCategory().getId());
        body.put("active", true);
        body.put("version", version);
        return body;
    }
}
