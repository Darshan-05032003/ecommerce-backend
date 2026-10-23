package com.darshan.ecommerce.common.security;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Proves the IDOR fix: no customer URL carries a user id, so user B cannot reach user A's data
// by editing the request, and the admin view of another user's data is reserved for admins.
@IntegrationTest
class OwnershipIsolationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    private Long aliceId;
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api.cleanDatabase();
        aliceId = api.registerCustomer("alice@example.com", PASSWORD);
        api.registerCustomer("bob@example.com", PASSWORD);
        api.createAdmin("admin@example.com", PASSWORD);
        aliceToken = api.login("alice@example.com", PASSWORD).accessToken();
        bobToken = api.login("bob@example.com", PASSWORD).accessToken();
        adminToken = api.login("admin@example.com", PASSWORD).accessToken();

        Long productId = api.createProduct("ISO-1", 10).getId();
        mockMvc.perform(post("/api/v1/me/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("productId", productId, "quantity", 2)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("shippingAddress", "1 Main Street, Pune")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(aliceId));
    }

    @Test
    void eachCustomerSeesOnlyTheirOwnOrdersAndCart() throws Exception {
        mockMvc.perform(get("/api/v1/me/orders").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(aliceId))
                .andExpect(jsonPath("$.content[0].items[0].quantity").value(2));

        mockMvc.perform(get("/api/v1/me/orders").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/v1/me/cart").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void aUserIdInTheRequestIsNeverUsedToSelectAnotherUsersData() throws Exception {
        mockMvc.perform(get("/api/v1/me/orders").param("userId", String.valueOf(aliceId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/users/" + aliceId + "/orders").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + aliceId + "/cart").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanViewAnotherUsersOrders() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + aliceId + "/orders").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));

        mockMvc.perform(get("/api/v1/admin/users/" + aliceId + "/orders").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(aliceId));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
