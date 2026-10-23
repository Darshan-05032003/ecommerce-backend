package com.darshan.ecommerce.common.observability;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorEndpointsIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    private String customerToken;
    private String adminToken;

    @BeforeAll
    void createUsers() throws Exception {
        api.cleanDatabase();
        api.registerCustomer("customer@actuator.example", PASSWORD);
        api.createAdmin("admin@actuator.example", PASSWORD);
        customerToken = api.login("customer@actuator.example", PASSWORD).accessToken();
        adminToken = api.login("admin@actuator.example", PASSWORD).accessToken();
    }

    @Test
    void anonymousHealthShowsTheStatusOnly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void customersDoNotSeeHealthDetailsEither() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void adminsSeeTheDatabaseAndRedisComponents() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.database").value("MySQL"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.mail").doesNotExist());
    }

    @Test
    void readinessChecksTheDatabaseAndRedisButLivenessDoesNot() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));

        // The liveness group has a single member without details, so the body is that member's status.
        mockMvc.perform(get("/actuator/health/liveness").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void infoShowsBuildDetailsToAdmins() throws Exception {
        mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("ecommerce-backend"))
                .andExpect(jsonPath("$.build.group").value("com.darshan"))
                .andExpect(jsonPath("$.build.version").isNotEmpty());
    }

    @Test
    void prometheusScrapeContainsTheBusinessMetricsBeforeAnyTraffic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# TYPE orders_placed_total counter")))
                .andExpect(content().string(containsString("payments_failed_total{")))
                .andExpect(content().string(containsString("method=\"CARD\"")))
                .andExpect(content().string(containsString("reason=\"declined\"")))
                .andExpect(content().string(containsString("orders_placement_seconds_count{")))
                .andExpect(content().string(containsString("outcome=\"created\"")))
                .andExpect(content().string(containsString("application=\"ecommerce-backend\"")));
    }

    @Test
    void metricsEndpointDescribesAMeter() throws Exception {
        mockMvc.perform(get("/actuator/metrics/payments.failed").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("payments.failed"))
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'method')].values[*]")
                        .value(containsInAnyOrder("CARD", "UPI", "COD")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
            "/actuator/loggers", "/actuator/threaddump", "/actuator/shutdown"})
    void sensitiveEndpointsAreNotExposedEvenToAdmins(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("JWT_SECRET"))));
    }

    @Test
    void theEndpointIndexIsForAdminsAndListsOnlyTheExposedEndpoints() throws Exception {
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.health").exists())
                .andExpect(jsonPath("$._links.info").exists())
                .andExpect(jsonPath("$._links.metrics").exists())
                .andExpect(jsonPath("$._links.prometheus").exists())
                .andExpect(jsonPath("$._links.env").doesNotExist())
                .andExpect(jsonPath("$._links.beans").doesNotExist());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
