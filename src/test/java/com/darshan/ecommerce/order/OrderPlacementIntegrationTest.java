package com.darshan.ecommerce.order;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.UserRepository;

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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderPlacementIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";
    private static final int BUYERS = 20;
    private static final int LAST_UNITS = 5;

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
    private JdbcTemplate jdbcTemplate;

    private final List<Long> buyerIds = new ArrayList<>();
    private final List<String> buyerTokens = new ArrayList<>();
    private Long aliceId;
    private String aliceToken;
    private Category category;

    // Twenty buyers for the race plus Alice for the single-request cases. Each logs in once:
    // the login limit is per client and email, so twenty different emails are fine.
    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        for (int i = 1; i <= BUYERS; i++) {
            String email = "buyer" + i + "@race.example";
            buyerIds.add(api.registerCustomer(email, PASSWORD));
            buyerTokens.add(api.login(email, PASSWORD).accessToken());
        }
        aliceId = api.registerCustomer("alice@place.example", PASSWORD);
        aliceToken = api.login("alice@place.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        category = api.createCategory("Books");
    }

    @Test
    void twentyBuyersRaceForTheLastFiveUnitsAndExactlyFiveWin() throws Exception {
        Product lastUnits = api.createProduct("RACE-001", new BigDecimal("49.99"), LAST_UNITS, category);
        buyerIds.forEach(id -> addToCart(id, lastUnits, 1));

        ExecutorService executor = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (String token : buyerTokens) {
                Callable<MockHttpServletResponse> buy = () -> {
                    startGate.await();
                    return placeOrder(token, UUID.randomUUID().toString()).andReturn().getResponse();
                };
                futures.add(executor.submit(buy));
            }
            startGate.countDown();

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            List<MockHttpServletResponse> succeeded = responses.stream().filter(r -> r.getStatus() == 201).toList();
            List<MockHttpServletResponse> rejected = responses.stream().filter(r -> r.getStatus() == 422).toList();
            assertThat(succeeded).hasSize(LAST_UNITS);
            assertThat(rejected).hasSize(BUYERS - LAST_UNITS);
            for (MockHttpServletResponse response : rejected) {
                assertThat(api.read(response.getContentAsString()).get("errorCode").asString())
                        .isEqualTo(ProductService.OUT_OF_STOCK);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(productRepository.findById(lastUnits.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(LAST_UNITS);
        assertThat(count("SELECT COUNT(*) FROM order_items")).isEqualTo(LAST_UNITS);
        assertThat(count("SELECT COUNT(*) FROM order_status_history")).isEqualTo(LAST_UNITS);
        assertThat(count("SELECT COUNT(*) FROM idempotency_keys")).isEqualTo(LAST_UNITS);
        // A buyer either has an order and an empty cart, or no order and the line still in the cart.
        for (Long buyerId : buyerIds) {
            int orders = count("SELECT COUNT(*) FROM orders WHERE user_id = " + buyerId);
            int cartLines = count("SELECT COUNT(*) FROM cart_items WHERE user_id = " + buyerId);
            assertThat(orders + cartLines).as("buyer %d", buyerId).isEqualTo(1);
        }
    }

    @Test
    void placingAnOrderSnapshotsPricesAndEmptiesTheCart() throws Exception {
        Product book = api.createProduct("BK-001", new BigDecimal("19.99"), 10, category);
        Product pen = api.createProduct("PN-001", new BigDecimal("5.05"), 10, category);
        addToCart(aliceId, book, 2);
        addToCart(aliceId, pen, 3);

        String body = placeOrder(aliceToken, "key-snapshot-prices")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(matchesPattern("ORD-2026-\\d{6}")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.shippingAddress").value("1 Main Street, Pune"))
                .andReturn().getResponse().getContentAsString();
        JsonNode order = api.read(body);
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("55.13");
        assertThat(order.get("items").get(0).get("sku").asString()).isEqualTo("BK-001");
        assertThat(order.get("items").get(0).get("lineTotal").decimalValue()).isEqualByComparingTo("39.98");
        assertThat(order.get("items").get(1).get("lineTotal").decimalValue()).isEqualByComparingTo("15.15");

        jdbcTemplate.update("UPDATE products SET price = 25.00 WHERE id = ?", book.getId());
        BigDecimal storedUnitPrice = jdbcTemplate.queryForObject(
                "SELECT price FROM order_items WHERE product_id = ?", BigDecimal.class, book.getId());
        assertThat(storedUnitPrice).isEqualByComparingTo("19.99");
        assertThat(cartItemRepository.findCartWithProducts(aliceId)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE from_status IS NULL AND to_status = 'CREATED'"))
                .isEqualTo(1);
    }

    @Test
    void anOutOfStockSecondLineRollsBackTheWholeOrder() throws Exception {
        Product book = api.createProduct("BK-001", new BigDecimal("19.99"), 10, category);
        Product scarce = api.createProduct("SC-001", new BigDecimal("9.99"), 1, category);
        addToCart(aliceId, book, 2);
        addToCart(aliceId, scarce, 1);
        jdbcTemplate.update("UPDATE products SET stock_quantity = 0 WHERE id = ?", scarce.getId());

        placeOrder(aliceToken, "key-rollback")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(ProductService.OUT_OF_STOCK));

        assertThat(productRepository.findById(book.getId()).orElseThrow().getStockQuantity())
                .as("the first line's reservation was rolled back").isEqualTo(10);
        assertThat(cartItemRepository.findCartWithProducts(aliceId)).hasSize(2);
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_items")).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_status_history")).isZero();
        assertThat(count("SELECT COUNT(*) FROM idempotency_keys")).isZero();
    }

    @Test
    void aProductWithdrawnAfterItWasAddedToTheCartIsRejected() throws Exception {
        Product book = api.createProduct("BK-001", new BigDecimal("19.99"), 10, category);
        Product withdrawn = api.createProduct("WD-001", new BigDecimal("9.99"), 10, category);
        addToCart(aliceId, book, 1);
        addToCart(aliceId, withdrawn, 1);
        withdrawn.setActive(false);
        productRepository.save(withdrawn);

        placeOrder(aliceToken, "key-withdrawn")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(ProductService.PRODUCT_UNAVAILABLE));

        assertThat(productRepository.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
    }

    @Test
    void anEmptyCartCannotBeOrdered() throws Exception {
        placeOrder(aliceToken, "key-empty")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(OrderService.CART_EMPTY));
    }

    @Test
    void aShippingAddressIsRequired() throws Exception {
        addToCart(aliceId, api.createProduct("BK-001", 10), 1);

        mockMvc.perform(post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("shippingAddress", " ")))
                        .header(OrderController.IDEMPOTENCY_KEY, "key-no-address")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingAddress"));
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
    }

    private ResultActions placeOrder(String token, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("shippingAddress", "1 Main Street, Pune")))
                .header(OrderController.IDEMPOTENCY_KEY, key)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void addToCart(Long userId, Product product, int quantity) {
        cartItemRepository.save(new CartItem(userRepository.getReferenceById(userId), product, quantity));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
