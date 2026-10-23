package com.darshan.ecommerce.product;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every product sits in its own category, so a lazy category load per row would show up as one
// extra SELECT per product on the page.
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductListQueryCountTest {

    private static final int PRODUCTS = 25;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeAll
    void createProductsInDistinctCategories() {
        api.cleanCatalogue();
        for (int i = 1; i <= PRODUCTS; i++) {
            String suffix = String.format("%03d", i);
            api.createProduct("QC-" + suffix, new BigDecimal("10.00"), 1, api.createCategory("Query count " + suffix));
        }
    }

    // Two statements whatever the page size: the page SELECT joined to categories and the COUNT.
    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20})
    void listingAPageUsesTheSameNumberOfStatementsForAnyPageSize(int pageSize) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/products").param("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(pageSize)))
                .andExpect(jsonPath("$.content[0].category.name").value("Query count 001"));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
