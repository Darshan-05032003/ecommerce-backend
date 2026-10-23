package com.darshan.ecommerce.common.observability;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.user.UserRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The registry is shared by every test in the cached context, so each test compares before and
// after values instead of expecting absolute counts.
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessMetricsIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    private Long customerId;
    private String customerToken;
    private String adminToken;
    private Product product;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        customerId = api.registerCustomer("customer@metrics.example", PASSWORD);
        api.createAdmin("admin@metrics.example", PASSWORD);
        customerToken = api.login("customer@metrics.example", PASSWORD).accessToken();
        adminToken = api.login("admin@metrics.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        product = api.createProduct("MET-1", new BigDecimal("12.00"), 10, api.createCategory("Metrics"));
    }

    @Test
    void placingAnOrderCountsItAndTimesTheRequest() throws Exception {
        double placedBefore = ordersPlaced();
        long createdBefore = placements("created");

        String key = UUID.randomUUID().toString();
        fillCart();
        placeOrder(key).andExpect(status().isCreated());

        assertThat(ordersPlaced()).isEqualTo(placedBefore + 1);
        assertThat(placements("created")).isEqualTo(createdBefore + 1);
    }

    @Test
    void aReplayedRequestIsTimedButNotCountedAsANewOrder() throws Exception {
        String key = UUID.randomUUID().toString();
        fillCart();
        placeOrder(key).andExpect(status().isCreated());
        double placedBefore = ordersPlaced();
        long replayedBefore = placements("replayed");

        placeOrder(key).andExpect(status().isCreated());

        assertThat(ordersPlaced()).isEqualTo(placedBefore);
        assertThat(placements("replayed")).isEqualTo(replayedBefore + 1);
    }

    @Test
    void aRejectedPlacementIsTimedWithTheRejectedOutcome() throws Exception {
        double placedBefore = ordersPlaced();
        long rejectedBefore = placements("rejected");

        placeOrder(UUID.randomUUID().toString()).andExpect(status().isUnprocessableContent());

        assertThat(ordersPlaced()).isEqualTo(placedBefore);
        assertThat(placements("rejected")).isEqualTo(rejectedBefore + 1);
    }

    @Test
    void aDeclinedCardPaymentIncrementsPaymentsFailedForCard() throws Exception {
        double declinedBefore = paymentsFailed("CARD", "declined");
        double upiBefore = paymentsFailed("UPI", "declined");

        long orderId = placedOrderId();
        pay(orderId, Map.of("method", "CARD", "paymentToken", "tok_decline"))
                .andExpect(status().isUnprocessableContent());

        assertThat(paymentsFailed("CARD", "declined")).isEqualTo(declinedBefore + 1);
        assertThat(paymentsFailed("UPI", "declined")).isEqualTo(upiBefore);
    }

    @Test
    void aProviderTimeoutIsCountedSeparatelyFromADecline() throws Exception {
        double timeoutBefore = paymentsFailed("UPI", "timeout");
        double declinedBefore = paymentsFailed("UPI", "declined");

        long orderId = placedOrderId();
        pay(orderId, Map.of("method", "UPI", "upiId", "timeout@okbank")).andExpect(status().isGatewayTimeout());

        assertThat(paymentsFailed("UPI", "timeout")).isEqualTo(timeoutBefore + 1);
        assertThat(paymentsFailed("UPI", "declined")).isEqualTo(declinedBefore);
    }

    @Test
    void aSuccessfulPaymentIsNotAFailure() throws Exception {
        double failuresBefore = registry.get("payments.failed").counters().stream().mapToDouble(Counter::count).sum();

        long orderId = placedOrderId();
        pay(orderId, Map.of("method", "COD")).andExpect(status().isCreated());

        assertThat(registry.get("payments.failed").counters().stream().mapToDouble(Counter::count).sum())
                .isEqualTo(failuresBefore);
    }

    @Test
    void theScrapeReflectsTheNewCount() throws Exception {
        long orderId = placedOrderId();
        pay(orderId, Map.of("method", "CARD", "paymentToken", "tok_decline"))
                .andExpect(status().isUnprocessableContent());
        int declined = (int) paymentsFailed("CARD", "declined");

        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*payments_failed_total\\{[^}]*method=\"CARD\"[^}]*reason=\"declined\"[^}]*} "
                                + declined + "\\.0\n.*")));
    }

    private double ordersPlaced() {
        return registry.get("orders.placed").counter().count();
    }

    private long placements(String outcome) {
        return registry.get("orders.placement").tag("outcome", outcome).timer().count();
    }

    private double paymentsFailed(String method, String reason) {
        return registry.get("payments.failed").tag("method", method).tag("reason", reason).counter().count();
    }

    private void fillCart() {
        cartItemRepository.save(new CartItem(userRepository.getReferenceById(customerId), product, 1));
    }

    private long placedOrderId() throws Exception {
        fillCart();
        String body = placeOrder(UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return api.read(body).get("id").asLong();
    }

    private ResultActions placeOrder(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("shippingAddress", "1 Metrics Road"))));
    }

    private ResultActions pay(long orderId, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(body)));
    }
}
