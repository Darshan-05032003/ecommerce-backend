package com.darshan.ecommerce;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.Role;
import com.darshan.ecommerce.user.RoleRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestSupport {

    @Bean
    TestApi testApi(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                    UserRepository userRepository, RoleRepository roleRepository,
                    CategoryRepository categoryRepository, ProductRepository productRepository,
                    PasswordEncoder passwordEncoder, CacheManager cacheManager) {
        return new TestApi(mockMvc, objectMapper, jdbcTemplate, userRepository, roleRepository,
                categoryRepository, productRepository, passwordEncoder, cacheManager);
    }

    public static final class TestApi {

        public record Tokens(String accessToken, String refreshToken) {
        }

        private final MockMvc mockMvc;
        private final ObjectMapper objectMapper;
        private final JdbcTemplate jdbcTemplate;
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final CategoryRepository categoryRepository;
        private final ProductRepository productRepository;
        private final PasswordEncoder passwordEncoder;
        private final CacheManager cacheManager;

        TestApi(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                UserRepository userRepository, RoleRepository roleRepository,
                CategoryRepository categoryRepository, ProductRepository productRepository,
                PasswordEncoder passwordEncoder, CacheManager cacheManager) {
            this.mockMvc = mockMvc;
            this.objectMapper = objectMapper;
            this.jdbcTemplate = jdbcTemplate;
            this.userRepository = userRepository;
            this.roleRepository = roleRepository;
            this.categoryRepository = categoryRepository;
            this.productRepository = productRepository;
            this.passwordEncoder = passwordEncoder;
            this.cacheManager = cacheManager;
        }

        // Child tables first; replaced_by points at the same table, so it is cleared before the delete.
        // The Redis caches are shared by every test in the context, so they are emptied as well.
        public void cleanDatabase() {
            jdbcTemplate.update("UPDATE refresh_tokens SET replaced_by = NULL");
            for (String table : List.of("refresh_tokens", "idempotency_keys", "order_status_history", "order_items",
                    "orders", "cart_items", "user_roles", "users", "products", "categories")) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
            clearCaches();
        }

        public void cleanCatalogue() {
            for (String table : List.of("idempotency_keys", "order_status_history", "order_items", "orders",
                    "cart_items", "products", "categories")) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
            clearCaches();
        }

        public void clearCaches() {
            cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        }

        public String json(Object value) {
            return objectMapper.writeValueAsString(value);
        }

        public JsonNode read(String body) {
            return objectMapper.readTree(body);
        }

        public Long registerCustomer(String email, String password) throws Exception {
            String body = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password, "fullName", "Test User"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return read(body).get("id").asLong();
        }

        public Long createAdmin(String email, String password) {
            Role admin = roleRepository.findByName(Role.ADMIN).orElseThrow();
            return userRepository.save(new User(email, passwordEncoder.encode(password), "Admin", Set.of(admin))).getId();
        }

        public Tokens login(String email, String password) throws Exception {
            String body = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = read(body);
            return new Tokens(node.get("accessToken").asString(), node.get("refreshToken").asString());
        }

        public Category createCategory(String name) {
            return categoryRepository.findByName(name).orElseGet(() -> categoryRepository.save(new Category(name, null)));
        }

        public Product createProduct(String sku, int stock) {
            return createProduct(sku, new BigDecimal("19.99"), stock, createCategory("General"));
        }

        public Product createProduct(String sku, BigDecimal price, int stock, Category category) {
            return productRepository.save(new Product(sku, "Product " + sku, "Description", price, stock, category));
        }
    }
}
