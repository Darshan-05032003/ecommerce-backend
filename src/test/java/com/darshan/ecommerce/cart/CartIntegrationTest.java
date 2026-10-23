package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Users log in once for the class: login is rate limited per client and email, and a fresh login
// per test would exhaust the bucket. Each test starts with an empty catalogue and empty carts.
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CartIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long aliceId;
    private String aliceToken;
    private String bobToken;
    private Product book;
    private Product pen;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        aliceId = api.registerCustomer("alice@cart.example", PASSWORD);
        api.registerCustomer("bob@cart.example", PASSWORD);
        aliceToken = api.login("alice@cart.example", PASSWORD).accessToken();
        bobToken = api.login("bob@cart.example", PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        Category category = api.createCategory("Stationery");
        book = api.createProduct("BK-001", new BigDecimal("19.99"), 50, category);
        pen = api.createProduct("PN-001", new BigDecimal("5.05"), 50, category);
    }

    @Test
    void runsTheWholeCartFlowWithLineAndCartTotals() throws Exception {
        add(aliceToken, book.getId(), 2).andExpect(status().isOk());
        assertTotal(cart(aliceToken), "39.98");

        JsonNode merged = read(add(aliceToken, book.getId(), 3).andExpect(status().isOk()));
        assertThat(merged.get("lines")).hasSize(1);
        assertThat(merged.get("lines").get(0).get("quantity").asInt()).isEqualTo(5);
        assertTotal(merged, "99.95");

        JsonNode twoLines = read(add(aliceToken, pen.getId(), 1).andExpect(status().isOk()));
        assertThat(twoLines.get("lines")).hasSize(2);
        JsonNode bookLine = twoLines.get("lines").get(0);
        assertThat(bookLine.get("productId").asLong()).isEqualTo(book.getId());
        assertThat(bookLine.get("sku").asString()).isEqualTo("BK-001");
        assertThat(bookLine.get("name").asString()).isEqualTo("Product BK-001");
        assertThat(bookLine.get("unitPrice").decimalValue()).isEqualByComparingTo("19.99");
        assertThat(bookLine.get("lineTotal").decimalValue()).isEqualByComparingTo("99.95");
        assertThat(twoLines.get("totalQuantity").asInt()).isEqualTo(6);
        assertTotal(twoLines, "105.00");

        JsonNode updated = read(update(aliceToken, book.getId(), 4).andExpect(status().isOk()));
        assertTotal(updated, "85.01");

        mockMvc.perform(delete("/api/v1/me/cart/items/" + pen.getId()).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNoContent());
        JsonNode afterRemove = cart(aliceToken);
        assertThat(afterRemove.get("lines")).hasSize(1);
        assertTotal(afterRemove, "79.96");

        mockMvc.perform(delete("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNoContent());
        String empty = mockMvc.perform(get("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(empty).isEqualTo("{\"lines\":[],\"totalQuantity\":0,\"total\":0.00}");
    }

    @Test
    void anotherUsersCartIsIsolated() throws Exception {
        add(aliceToken, book.getId(), 2).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)));
        update(bobToken, book.getId(), 9).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/me/cart/items/" + book.getId()).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNoContent());

        JsonNode alicesCart = cart(aliceToken);
        assertThat(alicesCart.get("lines")).hasSize(1);
        assertThat(alicesCart.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void aMergeAboveTenIsRejectedAndTheLineKeepsItsQuantity() throws Exception {
        add(aliceToken, book.getId(), 8).andExpect(status().isOk());

        add(aliceToken, book.getId(), 3)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("CART_LINE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("A cart line can hold at most 10 units, this change would make it 11"));

        assertThat(cart(aliceToken).get("lines").get(0).get("quantity").asInt()).isEqualTo(8);
    }

    @Test
    void quantitiesAboveTheStockAreRejected() throws Exception {
        Product scarce = api.createProduct("SC-001", new BigDecimal("3.00"), 3, api.createCategory("Stationery"));

        add(aliceToken, scarce.getId(), 4)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        add(aliceToken, scarce.getId(), 2).andExpect(status().isOk());
        add(aliceToken, scarce.getId(), 2)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        update(aliceToken, scarce.getId(), 3).andExpect(status().isOk());
    }

    @Test
    void unknownAndSoftDeletedProductsCannotBeAdded() throws Exception {
        add(aliceToken, 999_999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found with id 999999"));

        book.setActive(false);
        productRepository.save(book);
        add(aliceToken, book.getId(), 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void invalidQuantitiesNeverReachTheService() throws Exception {
        add(aliceToken, book.getId(), 11)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
        update(aliceToken, book.getId(), 0).andExpect(status().isBadRequest());
    }

    @Test
    void theCartShowsTheCurrentPriceUntilAnOrderIsPlaced() throws Exception {
        add(aliceToken, book.getId(), 2).andExpect(status().isOk());

        Product reloaded = productRepository.findById(book.getId()).orElseThrow();
        reloaded.setPrice(new BigDecimal("17.49"));
        productRepository.save(reloaded);

        JsonNode cart = cart(aliceToken);
        assertThat(cart.get("lines").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("17.49");
        assertTotal(cart, "34.98");
    }

    // A double click sends the same add twice at the same moment. Both requests see an empty cart;
    // the unique key lets one insert win and the other is retried and merged, so nothing is lost.
    @Test
    void concurrentAddsOfTheSameProductEndInOneMergedLine() throws Exception {
        int requests = 5;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<CartResponse>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<CartResponse> task = () -> {
                    start.await();
                    return cartService.addItem(aliceId, new AddCartItemRequest(book.getId(), 1));
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<CartResponse> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<CartItem> lines = cartItemRepository.findCartWithProducts(aliceId);
        assertThat(lines).singleElement().extracting(CartItem::getQuantity).isEqualTo(requests);
    }

    private ResultActions add(String token, Long productId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/me/cart/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("productId", productId, "quantity", quantity))));
    }

    private ResultActions update(String token, Long productId, int quantity) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/cart/items/" + productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("quantity", quantity))));
    }

    private JsonNode cart(String token) throws Exception {
        return read(mockMvc.perform(get("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return api.read(result.andReturn().getResponse().getContentAsString());
    }

    private static void assertTotal(JsonNode cart, String expected) {
        assertThat(cart.get("total").decimalValue()).isEqualByComparingTo(expected);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
