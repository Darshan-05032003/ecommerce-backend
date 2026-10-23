package com.darshan.ecommerce.order;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.persistence.TestClockConfiguration;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderIdempotencyIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";
    private static final String ADDRESS = "221B Baker Street, London";

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
    private IdempotencyKeyStore keyStore;

    @Autowired
    private IdempotencyKeyCleanup cleanup;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long aliceId;
    private Long bobId;
    private String aliceToken;
    private String bobToken;
    private Product book;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        aliceId = api.registerCustomer("alice@idem.example", PASSWORD);
        bobId = api.registerCustomer("bob@idem.example", PASSWORD);
        aliceToken = api.login("alice@idem.example", PASSWORD).accessToken();
        bobToken = api.login("bob@idem.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        book = api.createProduct("IDM-001", 10);
    }

    @AfterEach
    void resetClock() {
        clock.set(TestClockConfiguration.START);
    }

    @Test
    void retryingWithTheSameKeyReturnsTheOriginalResponseAndPlacesOneOrder() throws Exception {
        addToCart(aliceId, book, 2);

        MockHttpServletResponse first = mockMvc.perform(placeOrder(aliceToken, "key-retry-1", ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(OrderController.IDEMPOTENT_REPLAYED))
                .andReturn().getResponse();
        MockHttpServletResponse second = mockMvc.perform(placeOrder(aliceToken, "key-retry-1", ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(header().string(OrderController.IDEMPOTENT_REPLAYED, "true"))
                .andReturn().getResponse();

        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getHeader(HttpHeaders.LOCATION)).isEqualTo(first.getHeader(HttpHeaders.LOCATION));
        assertThat(countOrders(aliceId)).isEqualTo(1);
        assertThat(stockOf(book)).isEqualTo(8);
    }

    @Test
    void theReplayIsTheStoredSnapshotEvenAfterTheOrderChanged() throws Exception {
        addToCart(aliceId, book, 1);
        String original = mockMvc.perform(placeOrder(aliceToken, "key-snapshot", ADDRESS))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE user_id = ?", aliceId);

        mockMvc.perform(placeOrder(aliceToken, "key-snapshot", ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));
        assertThat(original).contains("\"status\":\"CREATED\"");
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() throws Exception {
        addToCart(aliceId, book, 1);
        mockMvc.perform(placeOrder(aliceToken, "key-mismatch", ADDRESS)).andExpect(status().isCreated());
        addToCart(aliceId, book, 1);

        mockMvc.perform(placeOrder(aliceToken, "key-mismatch", "10 Downing Street, London"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(OrderPlacementService.KEY_REUSED));

        assertThat(countOrders(aliceId)).isEqualTo(1);
        assertThat(cartItemRepository.findCartWithProducts(aliceId)).hasSize(1);
    }

    @Test
    void aMissingKeyIsABadRequest() throws Exception {
        addToCart(aliceId, book, 1);

        mockMvc.perform(post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("shippingAddress", ADDRESS)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(OrderPlacementService.KEY_MISSING))
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(countOrders(aliceId)).isZero();
    }

    @Test
    void aMalformedKeyIsABadRequest() throws Exception {
        addToCart(aliceId, book, 1);

        mockMvc.perform(placeOrder(aliceToken, "not a valid key!", ADDRESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(OrderPlacementService.KEY_INVALID));
        mockMvc.perform(placeOrder(aliceToken, "k".repeat(101), ADDRESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(OrderPlacementService.KEY_INVALID));
    }

    @Test
    void keysBelongToOneUser() throws Exception {
        addToCart(aliceId, book, 1);
        addToCart(bobId, book, 2);

        mockMvc.perform(placeOrder(aliceToken, "shared-key", ADDRESS)).andExpect(status().isCreated());
        mockMvc.perform(placeOrder(bobToken, "shared-key", "Another address"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(OrderController.IDEMPOTENT_REPLAYED))
                .andExpect(jsonPath("$.userId").value(bobId));

        assertThat(countOrders(aliceId)).isEqualTo(1);
        assertThat(countOrders(bobId)).isEqualTo(1);
    }

    @Test
    void aFailedAttemptDoesNotUseUpTheKey() throws Exception {
        mockMvc.perform(placeOrder(aliceToken, "key-after-failure", ADDRESS))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(OrderService.CART_EMPTY));
        assertThat(keyStore.find(aliceId, "key-after-failure")).isEmpty();

        addToCart(aliceId, book, 1);
        mockMvc.perform(placeOrder(aliceToken, "key-after-failure", ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(OrderController.IDEMPOTENT_REPLAYED));
    }

    @Test
    void concurrentRequestsWithTheSameKeyPlaceExactlyOneOrder() throws Exception {
        addToCart(aliceId, book, 3);
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<MockHttpServletResponse> call = () -> {
                    startGate.await();
                    return mockMvc.perform(placeOrder(aliceToken, "key-concurrent", ADDRESS)).andReturn().getResponse();
                };
                futures.add(executor.submit(call));
            }
            startGate.countDown();

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
            assertThat(responses).extracting(MockHttpServletResponse::getContentAsString).containsOnly(
                    responses.getFirst().getContentAsString());
            assertThat(responses).filteredOn(r -> r.getHeader(OrderController.IDEMPOTENT_REPLAYED) == null).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(countOrders(aliceId)).isEqualTo(1);
        assertThat(stockOf(book)).isEqualTo(7);
    }

    @Test
    void theCleanupDeletesOnlyKeysOlderThanTheRetention() {
        keyStore.claim(aliceId, "old-key", "a".repeat(64));
        clock.advance(Duration.ofHours(1));
        keyStore.claim(aliceId, "recent-key", "b".repeat(64));
        clock.advance(Duration.ofHours(23).plusMinutes(30));

        int deleted = cleanup.deleteExpiredKeys();

        assertThat(deleted).isEqualTo(1);
        assertThat(keyStore.find(aliceId, "old-key")).isEmpty();
        assertThat(keyStore.find(aliceId, "recent-key")).isPresent();
    }

    private MockHttpServletRequestBuilder placeOrder(String token, String key, String address) {
        return post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("shippingAddress", address)))
                .header(OrderController.IDEMPOTENCY_KEY, key)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void addToCart(Long userId, Product product, int quantity) {
        cartItemRepository.save(new CartItem(userRepository.getReferenceById(userId), product, quantity));
    }

    private int countOrders(Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, userId);
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
    }
}
