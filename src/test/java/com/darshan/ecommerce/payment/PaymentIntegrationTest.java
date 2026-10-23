package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.persistence.TestClockConfiguration;
import com.darshan.ecommerce.order.OrderPlacementService;
import com.darshan.ecommerce.order.OrderRepository;
import com.darshan.ecommerce.order.PlaceOrderRequest;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.user.UserRepository;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderPlacementService placementService;

    @Autowired
    private PaymentAttemptLog attemptLog;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long aliceId;
    private String aliceToken;
    private String bobToken;
    private String adminToken;
    private Product book;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        aliceId = api.registerCustomer("alice@payments.example", PASSWORD);
        api.registerCustomer("bob@payments.example", PASSWORD);
        aliceToken = api.login("alice@payments.example", PASSWORD).accessToken();
        bobToken = api.login("bob@payments.example", PASSWORD).accessToken();
        api.createAdmin("admin@payments.example", PASSWORD);
        adminToken = api.login("admin@payments.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        book = api.createProduct("BK-PAY", new BigDecimal("24.50"), 10, api.createCategory("Books"));
    }

    @Test
    void aCardPaymentMarksTheOrderPaidAndRecordsTheChange() throws Exception {
        long orderId = placeOrder(2);

        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_visa"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.amount").value(49.00))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.gatewayReference").value(startsWith("SIM-CARD-")));

        mockMvc.perform(get("/api/v1/me/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.history[*].to").value(contains("CREATED", "PAID")))
                .andExpect(jsonPath("$.history[1].reason").value("Paid by CARD"))
                .andExpect(jsonPath("$.history[1].changedBy").value("alice@payments.example"));
        assertThat(attempts(orderId)).containsExactly(tuple("CARD", "SUCCEEDED", null));
    }

    @Test
    void upiAndCashOnDeliveryUseTheirOwnProcessors() throws Exception {
        long upiOrder = placeOrder(1);
        pay(upiOrder, aliceToken, Map.of("method", "UPI", "upiId", "alice@okbank"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gatewayReference").value(startsWith("SIM-UPI-")));

        long codOrder = placeOrder(1);
        pay(codOrder, aliceToken, Map.of("method", "COD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.gatewayReference").doesNotExist());

        assertThat(orderStatus(upiOrder)).isEqualTo("PAID");
        assertThat(orderStatus(codOrder)).isEqualTo("PAID");
    }

    @Test
    void aDeclinedPaymentLeavesTheOrderUnpaidButKeepsTheFailedAttempt() throws Exception {
        long orderId = placeOrder(1);

        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_decline"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.detail").value("The payment was declined: Declined by the issuer"));

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(historyRows(orderId)).isEqualTo(1);
        assertThat(attempts(orderId)).containsExactly(tuple("CARD", "FAILED", "Declined by the issuer"));

        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_mastercard"))
                .andExpect(status().isCreated());
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(attempts(orderId)).extracting(row -> row.toList().get(1)).containsExactly("FAILED", "SUCCEEDED");
    }

    @Test
    void aProviderTimeoutIsAGatewayTimeoutAndTheOrderStaysUnpaid() throws Exception {
        long orderId = placeOrder(1);

        pay(orderId, aliceToken, Map.of("method", "UPI", "upiId", "timeout@okbank"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_TIMEOUT"));

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(attempts(orderId)).containsExactly(tuple("UPI", "FAILED", "Payment provider timed out"));
    }

    // The attempt log writes in REQUIRES_NEW transactions. Here the surrounding transaction rolls
    // back: the row saved through the repository inside it disappears, the logged attempt stays.
    @Test
    void aLoggedAttemptSurvivesTheRollbackOfTheSurroundingTransaction() {
        long orderId = placeOrder(1);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            Payment logged = attemptLog.start(orderId, PaymentMethod.CARD, new BigDecimal("24.50"));
            attemptLog.fail(logged.getId(), "Declined by the issuer");
            paymentRepository.saveAndFlush(new Payment(orderRepository.getReferenceById(orderId), PaymentMethod.UPI,
                    new BigDecimal("24.50")));
            assertThat(attempts(orderId)).hasSize(2);
            status.setRollbackOnly();
        });

        assertThat(attempts(orderId)).containsExactly(tuple("CARD", "FAILED", "Declined by the issuer"));
    }

    @Test
    void aPaidOrderCannotBePaidAgain() throws Exception {
        long orderId = placeOrder(1);
        pay(orderId, aliceToken, Map.of("method", "COD")).andExpect(status().isCreated());

        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_visa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order cannot move from PAID to PAID"));

        assertThat(attempts(orderId)).hasSize(1);
    }

    @Test
    void concurrentPaymentsForOneOrderChargeItOnce() throws Exception {
        long orderId = placeOrder(1);
        int callers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_visa"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(201, 409).filteredOn(code -> code == 201).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(attempts(orderId)).extracting(row -> row.toList().get(1)).containsOnlyOnce("SUCCEEDED")
                .doesNotContain("INITIATED");
    }

    @Test
    void anAttemptInFlightBlocksASecondOne() throws Exception {
        long orderId = placeOrder(1);
        attemptLog.start(orderId, PaymentMethod.CARD, new BigDecimal("24.50"));

        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_visa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A payment for this order is already in progress"));

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(attempts(orderId)).containsExactly(tuple("CARD", "INITIATED", null));
    }

    @Test
    void aCancelledOrderCannotBePaid() throws Exception {
        long orderId = placeOrder(1);
        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        pay(orderId, aliceToken, Map.of("method", "COD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order cannot move from CANCELLED to PAID"));
        assertThat(attempts(orderId)).isEmpty();
    }

    @Test
    void anotherUsersOrderLooksLikeItDoesNotExist() throws Exception {
        long orderId = placeOrder(1);

        pay(orderId, bobToken, Map.of("method", "COD")).andExpect(status().isNotFound());
        pay(orderId + 1000, aliceToken, Map.of("method", "COD")).andExpect(status().isNotFound());

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(attempts(orderId)).isEmpty();
    }

    @Test
    void malformedPaymentRequestsAreRejectedBeforeAnAttemptIsRecorded() throws Exception {
        long orderId = placeOrder(1);

        pay(orderId, aliceToken, Map.of("method", "CARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_TOKEN_INVALID"));
        pay(orderId, aliceToken, Map.of("method", "UPI", "upiId", "not-a-upi-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UPI_ID_INVALID"));
        pay(orderId, aliceToken, Map.of("method", "CHEQUE")).andExpect(status().isBadRequest());
        pay(orderId, aliceToken, new HashMap<>())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("method"));

        assertThat(attempts(orderId)).isEmpty();
    }

    @Test
    void cancellingAPaidOrderRefundsThePaymentAndReturnsTheStock() throws Exception {
        long orderId = placeOrder(3);
        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_visa")).andExpect(status().isCreated());

        cancel(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[*].to").value(contains("CREATED", "PAID", "CANCELLED")));

        assertThat(refundedAt(orderId)).isEqualTo(TestClockConfiguration.START);
        assertThat(stockOf(book)).isEqualTo(10);
    }

    @Test
    void aRejectedRefundLeavesThePaidOrderAndItsStockUntouched() throws Exception {
        long orderId = placeOrder(3);
        pay(orderId, aliceToken, Map.of("method", "CARD", "paymentToken", "tok_refundfail"))
                .andExpect(status().isCreated());

        cancel(orderId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_REFUND_FAILED"));

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(historyRows(orderId)).isEqualTo(2);
        assertThat(refundedAt(orderId)).isNull();
        assertThat(stockOf(book)).isEqualTo(7);
    }

    @Test
    void anAdminCancellingAPaidOrderAlsoRefundsIt() throws Exception {
        long orderId = placeOrder(1);
        pay(orderId, aliceToken, Map.of("method", "COD")).andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("status", "CANCELLED", "reason", "Address not serviceable"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(refundedAt(orderId)).isNotNull();
        assertThat(stockOf(book)).isEqualTo(10);
    }

    @Test
    void anOrderMarkedPaidWithoutARecordedPaymentIsCancelledWithoutARefund() throws Exception {
        long orderId = placeOrder(1);
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("status", "PAID"))))
                .andExpect(status().isOk());

        cancel(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(attempts(orderId)).isEmpty();
    }

    private ResultActions cancel(long orderId) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)));
    }

    // Read through JPA so the instant is mapped exactly as the application wrote it, in UTC.
    private Instant refundedAt(long orderId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrder().getId() == orderId && payment.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst().orElseThrow().getRefundedAt();
    }

    private int stockOf(Product product) {
        return jdbcTemplate.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class,
                product.getId());
    }

    private long placeOrder(int quantity) {
        cartItemRepository.save(new CartItem(userRepository.getReferenceById(aliceId), book, quantity));
        return placementService.place(aliceId, UUID.randomUUID().toString(), new PlaceOrderRequest("1 Payment Road"))
                .order().id();
    }

    private ResultActions pay(long orderId, String token, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/payments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(body)));
    }

    private List<Tuple> attempts(long orderId) {
        return jdbcTemplate.query("SELECT method, status, failure_reason FROM payments WHERE order_id = ? ORDER BY id",
                (rs, n) -> tuple(rs.getString(1), rs.getString(2), rs.getString(3)), orderId);
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int historyRows(long orderId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                Integer.class, orderId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
