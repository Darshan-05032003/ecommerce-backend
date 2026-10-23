package com.darshan.ecommerce;

import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.common.persistence.TestClockConfiguration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// One customer journey through the public API, from registration to delivery, with every step
// going through HTTP, security, the services, MySQL and Redis exactly as in production. The other
// integration tests cover each step's edge cases; this one proves the steps fit together.
@IntegrationTest
class CheckoutFlowIntegrationTest {

    private static final String BUYER = "buyer@checkout.example";
    private static final String ADMIN = "admin@checkout.example";
    private static final String PASSWORD = "Str0ng!pw";
    private static final Instant START = TestClockConfiguration.START;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void emptyDatabase() {
        api.cleanDatabase();
        clock.set(START);
    }

    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void aCustomerRegistersBuysPaysAndReceivesTheOrder() throws Exception {
        api.createAdmin(ADMIN, PASSWORD);
        String admin = api.login(ADMIN, PASSWORD).accessToken();
        long books = createCategory(admin, "Books");
        long stationery = createCategory(admin, "Stationery");
        long bookId = createProduct(admin, "BK-DDIA-001", "Designing Data-Intensive Applications", "450.00", 5, books);
        createProduct(admin, "PEN-GEL-001", "Gel pen", "30.00", 100, stationery);
        double ordersPlacedBefore = registry.get("orders.placed").counter().count();
        double paymentsFailedBefore = paymentsFailed();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("email", BUYER, "password", PASSWORD, "fullName", "Buyer"))))
                .andExpect(status().isCreated());
        String buyer = api.login(BUYER, PASSWORD).accessToken();

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", Long.toString(books)).param("maxPrice", "500").param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bookId));

        perform(post("/api/v1/me/cart/items"), buyer, Map.of("productId", bookId, "quantity", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(900.00));

        JsonNode order = body(placeOrder(buyer, "checkout-flow-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(900.00)));
        long orderId = order.get("id").asLong();
        String orderNumber = order.get("orderNumber").asString();

        // The client did not see the 201 and retries with the same key: same order, stock taken once.
        placeOrder(buyer, "checkout-flow-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(get("/api/v1/products/" + bookId))
                .andExpect(jsonPath("$.stockQuantity").value(3));
        perform(get("/api/v1/me/cart"), buyer, null)
                .andExpect(jsonPath("$.lines").isEmpty());

        perform(post("/api/v1/me/orders/" + orderId + "/payments"), buyer,
                Map.of("method", "CARD", "paymentToken", "tok_visa"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        clock.advance(Duration.ofMinutes(2));
        perform(patch("/api/v1/admin/orders/" + orderId + "/status"), admin,
                Map.of("status", "SHIPPED", "reason", "Handed to the courier"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(3));
        perform(patch("/api/v1/admin/orders/" + orderId + "/status"), admin,
                Map.of("status", "DELIVERED", "reason", "Signed for by the customer"))
                .andExpect(status().isOk());

        perform(get("/api/v1/me/orders/" + orderId), buyer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history[*].to").value(contains("CREATED", "PAID", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.history[*].changedBy").value(contains(BUYER, BUYER, ADMIN, ADMIN)))
                .andExpect(jsonPath("$.history[*].reason").value(contains("Order placed", "Paid by CARD",
                        "Handed to the courier", "Signed for by the customer")))
                .andExpect(jsonPath("$.history[*].changedAt").value(contains(START.toString(), START.toString(),
                        START.plus(Duration.ofMinutes(2)).toString(), START.plus(Duration.ofMinutes(5)).toString())));

        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT method, status, amount, gateway_reference FROM payments WHERE order_id = ?", orderId);
        assertThat(payment).containsEntry("method", "CARD").containsEntry("status", "SUCCEEDED");
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo("900.00");
        assertThat((String) payment.get("gateway_reference")).startsWith("SIM-CARD-");
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, bookId))
                .isEqualTo(3);

        assertThat(registry.get("orders.placed").counter().count()).isEqualTo(ordersPlacedBefore + 1);
        assertThat(paymentsFailed()).isEqualTo(paymentsFailedBefore);

        // Emails are sent after commit on the async pool, so they are awaited, never slept for.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(mailSender.sentTo(BUYER)).extracting(mail -> mail.message().getSubject())
                        .containsExactlyInAnyOrder("Order " + orderNumber + " received",
                                "Order " + orderNumber + " confirmed"));
    }

    private long createCategory(String adminToken, String name) throws Exception {
        return body(perform(post("/api/v1/admin/categories"), adminToken, Map.of("name", name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createProduct(String adminToken, String sku, String name, String price, int stock, long categoryId)
            throws Exception {
        Map<String, Object> request = Map.of("sku", sku, "name", name, "description", name, "price", price,
                "stockQuantity", stock, "categoryId", categoryId);
        return body(perform(post("/api/v1/admin/products"), adminToken, request)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions placeOrder(String token, String idempotencyKey) throws Exception {
        return perform(post("/api/v1/me/orders").header("Idempotency-Key", idempotencyKey), token,
                Map.of("shippingAddress", "12 MG Road, Pune"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, Map<String, ?> body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(api.json(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return api.read(result.andReturn().getResponse().getContentAsString());
    }

    private double paymentsFailed() {
        return registry.get("payments.failed").counters().stream().mapToDouble(Counter::count).sum();
    }
}
