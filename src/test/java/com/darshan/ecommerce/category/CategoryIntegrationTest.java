package com.darshan.ecommerce.category;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    private String adminToken;

    @BeforeAll
    void createAdmin() throws Exception {
        api.cleanDatabase();
        api.createAdmin("admin@category.example", PASSWORD);
        adminToken = api.login("admin@category.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void resetCatalogue() {
        api.cleanCatalogue();
    }

    @Test
    void adminManagesCategoriesAndThePublicListStaysSortedByName() throws Exception {
        String location = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Toys", "description", "Games and toys"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Toys"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        api.createCategory("Books");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Games and toys"));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Books"))
                .andExpect(jsonPath("$[1].name").value("Toys"));

        long toysId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        mockMvc.perform(put("/api/v1/admin/categories/{id}", toysId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Games"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Games"));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[1].name").value("Games"));

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", toysId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void duplicateNamesAndCategoriesWithProductsAreConflicts() throws Exception {
        Category books = api.createCategory("Books");
        api.createProduct("BK-ANY-001", new BigDecimal("10.00"), 1, books);

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Books"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Category name already in use"));

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", books.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Category " + books.getId() + " still has products and cannot be deleted"));

        mockMvc.perform(get("/api/v1/categories/{id}", 999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankNamesAreRejectedWithAFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
