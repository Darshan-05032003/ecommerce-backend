package com.darshan.ecommerce.common.security;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.user.AdminUserController;
import com.darshan.ecommerce.user.UserResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityMatrixTest {

    private static final String PASSWORD = "Str0ng!pw";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    enum Caller { ANONYMOUS, CUSTOMER, ADMIN }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private AdminUserController adminUserController;

    private Long customerId;
    private String customerToken;
    private String adminToken;
    private Long categoryId;
    private Long emptyCategoryId;
    private Long productId;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        categoryId = api.createCategory("Matrix").getId();
        customerId = api.registerCustomer("customer@matrix.example", PASSWORD);
        api.createAdmin("admin@matrix.example", PASSWORD);
        customerToken = api.login("customer@matrix.example", PASSWORD).accessToken();
        adminToken = api.login("admin@matrix.example", PASSWORD).accessToken();
    }

    // A fresh product and an empty category per invocation keep the DELETE and PUT cases independent.
    @BeforeEach
    void freshProductAndCategory() {
        int n = COUNTER.incrementAndGet();
        productId = api.createProduct("MTX-" + n, 5).getId();
        emptyCategoryId = api.createCategory("Empty " + n).getId();
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                row(HttpMethod.GET, "/api/v1/me", 401, 200, 200),
                row(HttpMethod.GET, "/api/v1/me/cart", 401, 200, 200),
                row(HttpMethod.POST, "/api/v1/me/cart?productId={productId}&quantity=1", 401, 201, 201),
                row(HttpMethod.DELETE, "/api/v1/me/cart", 401, 204, 204),
                row(HttpMethod.GET, "/api/v1/me/orders", 401, 200, 200),
                row(HttpMethod.GET, "/api/v1/admin/users", 401, 403, 200),
                row(HttpMethod.GET, "/api/v1/admin/users/{customerId}", 401, 403, 200),
                row(HttpMethod.GET, "/api/v1/admin/users/{customerId}/orders", 401, 403, 200),
                row(HttpMethod.GET, "/api/v1/admin/products", 401, 403, 200),
                row(HttpMethod.GET, "/api/v1/admin/products/{productId}", 401, 403, 200),
                row(HttpMethod.POST, "/api/v1/admin/products", 401, 403, 201),
                row(HttpMethod.PUT, "/api/v1/admin/products/{productId}", 401, 403, 200),
                row(HttpMethod.DELETE, "/api/v1/admin/products/{productId}", 401, 403, 204),
                row(HttpMethod.POST, "/api/v1/admin/categories", 401, 403, 201),
                row(HttpMethod.PUT, "/api/v1/admin/categories/{emptyCategoryId}", 401, 403, 200),
                row(HttpMethod.DELETE, "/api/v1/admin/categories/{emptyCategoryId}", 401, 403, 204),
                row(HttpMethod.GET, "/api/v1/products", 200, 200, 200),
                row(HttpMethod.GET, "/api/v1/products/{productId}", 200, 200, 200),
                row(HttpMethod.GET, "/api/v1/categories", 200, 200, 200),
                row(HttpMethod.GET, "/api/v1/categories/{categoryId}", 200, 200, 200)
        ).flatMap(List::stream);
    }

    private static List<Arguments> row(HttpMethod method, String path, int anonymous, int customer, int admin) {
        return List.of(
                Arguments.of(method, path, Caller.ANONYMOUS, anonymous),
                Arguments.of(method, path, Caller.CUSTOMER, customer),
                Arguments.of(method, path, Caller.ADMIN, admin));
    }

    @ParameterizedTest(name = "{2} {0} {1} -> {3}")
    @MethodSource("matrix")
    void enforcesTheAccessMatrix(HttpMethod method, String pathTemplate, Caller caller, int expectedStatus) throws Exception {
        String path = pathTemplate
                .replace("{productId}", String.valueOf(productId))
                .replace("{customerId}", String.valueOf(customerId))
                .replace("{categoryId}", String.valueOf(categoryId))
                .replace("{emptyCategoryId}", String.valueOf(emptyCategoryId));
        MockHttpServletRequestBuilder builder = request(method, path);
        if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            Map<String, Object> body = path.contains("/categories") ? categoryBody() : productBody(method);
            builder.contentType(MediaType.APPLICATION_JSON).content(api.json(body));
        }
        tokenFor(caller).ifPresent(token -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        ResultActions result = mockMvc.perform(builder).andExpect(status().is(expectedStatus));

        if (expectedStatus == 401) {
            result.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Authentication required"))
                    .andExpect(jsonPath("$.instance").value(path.split("\\?")[0]))
                    .andExpect(jsonPath("$.correlationId").exists());
        }
        if (expectedStatus == 403) {
            result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Access denied"))
                    .andExpect(jsonPath("$.correlationId").exists());
        }
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void methodSecurityGuardsAdminOperationsIndependentlyOfTheUrlRules() {
        assertThatThrownBy(() -> adminUserController.list()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityLetsAdminsThrough() {
        assertThat(adminUserController.list()).extracting(UserResponse::email)
                .contains("customer@matrix.example", "admin@matrix.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products", "/api/users", "/api/users/{customerId}",
            "/api/users/{customerId}/cart", "/api/users/{customerId}/orders", "/api/auth/login"})
    void unversionedPathsNoLongerExist(String pathTemplate) throws Exception {
        String path = pathTemplate.replace("{customerId}", String.valueOf(customerId));

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value(path));
    }

    @Test
    void corsPreflightSucceedsOnlyForConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/products")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mockMvc.perform(options("/api/v1/products")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private Optional<String> tokenFor(Caller caller) {
        return switch (caller) {
            case ANONYMOUS -> Optional.empty();
            case CUSTOMER -> Optional.of(customerToken);
            case ADMIN -> Optional.of(adminToken);
        };
    }

    private Map<String, Object> productBody(HttpMethod method) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "sku", "NEW-MTX-" + String.format("%03d", COUNTER.incrementAndGet()), "name", "Matrix product",
                "description", "Created by the security matrix", "price", "10.00", "stockQuantity", 3,
                "categoryId", categoryId));
        if (method == HttpMethod.PUT) {
            body.put("active", true);
            body.put("version", 0);
        }
        return body;
    }

    private static Map<String, Object> categoryBody() {
        return Map.of("name", "Matrix category " + COUNTER.incrementAndGet());
    }
}
