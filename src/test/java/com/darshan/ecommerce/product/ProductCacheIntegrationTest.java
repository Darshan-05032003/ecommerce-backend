package com.darshan.ecommerce.product;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.common.config.CacheNames;
import com.darshan.ecommerce.common.config.CacheProperties;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductCacheIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CacheProperties cacheProperties;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminToken;
    private Category books;
    private Product product;

    @BeforeAll
    void createAdmin() throws Exception {
        api.cleanDatabase();
        api.createAdmin("admin@cache.example", PASSWORD);
        adminToken = api.login("admin@cache.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshProduct() {
        api.cleanCatalogue();
        books = api.createCategory("Books");
        product = api.createProduct("BK-CACHE-001", new BigDecimal("99.50"), 5, books);
    }

    @Test
    void secondReadIsServedFromRedisWithoutQueryingTheDatabase() throws Exception {
        String first = getProduct().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Statistics statistics = statistics();
        statistics.clear();
        String second = getProduct().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(statistics.getPrepareStatementCount()).isZero();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void cachedRecordKeepsTheBigDecimalScaleAndTheInstant() throws Exception {
        getProduct().andExpect(status().isOk());

        ProductResponse cached = productsCache().get(product.getId(), ProductResponse.class);
        Product stored = productRepository.findById(product.getId()).orElseThrow();

        assertThat(cached).isNotNull();
        assertThat(cached.price()).isEqualTo(new BigDecimal("99.50"));
        assertThat(cached.createdAt()).isEqualTo(stored.getCreatedAt());
        assertThat(cached.updatedAt()).isEqualTo(stored.getUpdatedAt());
        assertThat(cached.category()).isEqualTo(new ProductResponse.CategorySummary(books.getId(), "Books"));
        assertThat(redis.opsForValue().get(productKey()))
                .contains("\"price\":99.50")
                .doesNotContain("com.darshan");
    }

    @Test
    void entriesExpireAfterTheConfiguredTimeToLive() throws Exception {
        getProduct().andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

        assertThat(redis.getExpire(productKey(), TimeUnit.SECONDS))
                .isPositive()
                .isLessThanOrEqualTo(cacheProperties.ttl().products().toSeconds());
        assertThat(redis.getExpire(CacheNames.CATEGORIES + "::all", TimeUnit.SECONDS))
                .isGreaterThan(cacheProperties.ttl().products().toSeconds())
                .isLessThanOrEqualTo(cacheProperties.ttl().categories().toSeconds());
    }

    @Test
    void adminUpdateReplacesTheCachedEntry() throws Exception {
        getProduct().andExpect(jsonPath("$.price").value(99.50));

        update(updateBody("120.00", true, 0)).andExpect(status().isOk());

        Statistics statistics = statistics();
        statistics.clear();
        getProduct()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(120.00))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void rejectedUpdateLeavesTheCachedEntryAlone() throws Exception {
        getProduct().andExpect(status().isOk());

        update(updateBody("120.00", true, 7)).andExpect(status().isConflict());

        assertThat(productsCache().get(product.getId(), ProductResponse.class).price())
                .isEqualByComparingTo("99.50");
    }

    @Test
    void deactivatingOrDeletingRemovesTheEntrySoThePublicReadIsNotFound() throws Exception {
        getProduct().andExpect(status().isOk());
        update(updateBody("99.50", false, 0)).andExpect(status().isOk());

        assertThat(productsCache().get(product.getId())).isNull();
        getProduct().andExpect(status().isNotFound());

        Product other = api.createProduct("BK-CACHE-002", new BigDecimal("10.00"), 1, books);
        mockMvc.perform(get("/api/v1/products/{id}", other.getId())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/products/{id}", other.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(productsCache().get(other.getId())).isNull();
        mockMvc.perform(get("/api/v1/products/{id}", other.getId())).andExpect(status().isNotFound());
    }

    @Test
    void renamingTheCategoryEvictsProductsThatEmbedItsName() throws Exception {
        getProduct().andExpect(jsonPath("$.category.name").value("Books"));

        mockMvc.perform(put("/api/v1/admin/categories/{id}", books.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Printed books"))))
                .andExpect(status().isOk());

        getProduct().andExpect(jsonPath("$.category.name").value("Printed books"));
    }

    private ResultActions getProduct() throws Exception {
        return mockMvc.perform(get("/api/v1/products/{id}", product.getId()));
    }

    private ResultActions update(Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(body)));
    }

    private Map<String, Object> updateBody(String price, boolean active, int version) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", product.getSku());
        body.put("name", product.getName());
        body.put("description", product.getDescription());
        body.put("price", price);
        body.put("stockQuantity", product.getStockQuantity());
        body.put("categoryId", books.getId());
        body.put("active", active);
        body.put("version", version);
        return body;
    }

    private String productKey() {
        return CacheNames.PRODUCTS + "::" + product.getId();
    }

    private Cache productsCache() {
        return cacheManager.getCache(CacheNames.PRODUCTS);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
