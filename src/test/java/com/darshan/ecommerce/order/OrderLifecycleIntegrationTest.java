package com.darshan.ecommerce.order;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.common.config.CacheNames;
import com.darshan.ecommerce.common.persistence.TestClockConfiguration;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderLifecycleIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MutableClock clock;

    private Long aliceId;
    private String aliceToken;
    private String bobToken;
    private String adminToken;
    private Product book;
    private Product pen;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        aliceId = api.registerCustomer("alice@orders.example", PASSWORD);
        api.registerCustomer("bob@orders.example", PASSWORD);
        api.createAdmin("admin@orders.example", PASSWORD);
        aliceToken = api.login("alice@orders.example", PASSWORD).accessToken();
        bobToken = api.login("bob@orders.example", PASSWORD).accessToken();
        adminToken = api.login("admin@orders.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        Category category = api.createCategory("Stationery");
        book = api.createProduct("BK-001", new BigDecimal("19.99"), 10, category);
        pen = api.createProduct("PN-001", new BigDecimal("5.05"), 10, category);
    }

    @AfterEach
    void resetClock() {
        clock.set(TestClockConfiguration.START);
    }

    @Test
    void cancellingRestoresTheStockOfEveryLineAndRecordsTheChange() throws Exception {
        long orderId = placeOrder(Map.of(book, 3, pen, 2));
        assertThat(stockOf(book)).isEqualTo(7);
        assertThat(stockOf(pen)).isEqualTo(8);
        productService.getActiveById(book.getId());
        clock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].from").doesNotExist())
                .andExpect(jsonPath("$.history[0].to").value("CREATED"))
                .andExpect(jsonPath("$.history[0].changedAt").value(TestClockConfiguration.START.toString()))
                .andExpect(jsonPath("$.history[1].from").value("CREATED"))
                .andExpect(jsonPath("$.history[1].to").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].changedBy").value("alice@orders.example"))
                .andExpect(jsonPath("$.history[1].changedAt")
                        .value(TestClockConfiguration.START.plus(Duration.ofMinutes(5)).toString()))
                .andExpect(jsonPath("$.history[1].reason").value("Cancelled by customer"));

        assertThat(stockOf(book)).isEqualTo(10);
        assertThat(stockOf(pen)).isEqualTo(10);
        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(book.getId())).isNull();
    }

    @Test
    void anOrderCanBeCancelledOnlyOnce() throws Exception {
        long orderId = placeOrder(Map.of(book, 4));
        cancel(orderId, aliceToken).andExpect(status().isOk());

        cancel(orderId, aliceToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order cannot move from CANCELLED to CANCELLED"));

        assertThat(stockOf(book)).isEqualTo(10);
    }

    @Test
    void aShippedOrderCannotBeCancelled() throws Exception {
        long orderId = placeOrder(Map.of(book, 1));
        changeStatus(orderId, "PAID", null).andExpect(status().isOk());
        changeStatus(orderId, "SHIPPED", "Handed to courier").andExpect(status().isOk());

        cancel(orderId, aliceToken).andExpect(status().isConflict());

        assertThat(stockOf(book)).isEqualTo(9);
    }

    @Test
    void anotherUsersOrderLooksLikeItDoesNotExist() throws Exception {
        long orderId = placeOrder(Map.of(book, 2));

        mockMvc.perform(get("/api/v1/me/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        cancel(orderId, bobToken).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/orders/" + (orderId + 1000)).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/me/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));
        assertThat(stockOf(book)).isEqualTo(8);
    }

    @Test
    void anAdminMovesAnOrderThroughItsLifecycleAndEveryStepIsRecorded() throws Exception {
        long orderId = placeOrder(Map.of(book, 1));

        changeStatus(orderId, "PAID", "Payment confirmed").andExpect(jsonPath("$.status").value("PAID"));
        changeStatus(orderId, "SHIPPED", null).andExpect(jsonPath("$.status").value("SHIPPED"));
        changeStatus(orderId, "DELIVERED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].to").value(contains(
                        "CREATED", "PAID", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.history[1].changedBy").value("admin@orders.example"))
                .andExpect(jsonPath("$.history[1].reason").value("Payment confirmed"));

        changeStatus(orderId, "CANCELLED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order cannot move from DELIVERED to CANCELLED"));
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history", hasSize(4)));
    }

    @Test
    void anAdminCancellingAPaidOrderAlsoRestoresTheStock() throws Exception {
        long orderId = placeOrder(Map.of(pen, 5));
        changeStatus(orderId, "PAID", null).andExpect(status().isOk());

        changeStatus(orderId, "CANCELLED", "Customer called support")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[2].from").value("PAID"));

        assertThat(stockOf(pen)).isEqualTo(10);
    }

    @Test
    void anUnknownStatusInTheRequestIsABadRequest() throws Exception {
        long orderId = placeOrder(Map.of(pen, 1));

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOST\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
        changeStatus(orderId + 1000, "PAID", null).andExpect(status().isNotFound());
    }

    @Test
    void myOrdersArePagedNewestFirst() throws Exception {
        long first = placeOrder(Map.of(book, 1));
        clock.advance(Duration.ofMinutes(1));
        long second = placeOrder(Map.of(pen, 1));
        clock.advance(Duration.ofMinutes(1));
        long third = placeOrder(Map.of(book, 1, pen, 1));

        mockMvc.perform(get("/api/v1/me/orders").param("size", "2").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains((int) third, (int) second)))
                .andExpect(jsonPath("$.content[0].items", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/me/orders").param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(jsonPath("$.content[*].id").value(contains((int) first)));
        mockMvc.perform(get("/api/v1/me/orders").param("sort", "status").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAdminListCanBeFilteredByStatus() throws Exception {
        long paid = placeOrder(Map.of(book, 1));
        placeOrder(Map.of(pen, 1));
        changeStatus(paid, "PAID", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/orders").param("status", "PAID").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(paid));
        mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/admin/users/" + aliceId + "/orders").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private long placeOrder(Map<Product, Integer> lines) throws Exception {
        lines.forEach((product, quantity) ->
                cartItemRepository.save(new CartItem(userRepository.getReferenceById(aliceId), product, quantity)));
        String body = mockMvc.perform(post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("shippingAddress", "1 Main Street, Pune")))
                        .header(OrderController.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = api.read(body);
        return order.get("id").asLong();
    }

    private ResultActions cancel(long orderId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions changeStatus(long orderId, String status, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("reason", reason);
        return mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(api.json(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
