package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi.Tokens;
import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.common.persistence.TestClockConfiguration;
import com.darshan.ecommerce.common.web.CorrelationIdFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        api.cleanDatabase();
        clock.set(TestClockConfiguration.START);
    }

    @AfterEach
    void resetClock() {
        clock.set(TestClockConfiguration.START);
    }

    @Test
    void registrationCreatesACustomerAndNeverEchoesThePassword() throws Exception {
        String body = register("new@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.roles").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").value(TestClockConfiguration.START.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password").doesNotContain(PASSWORD);
        String stored = jdbcTemplate.queryForObject("SELECT password FROM users WHERE email = ?", String.class, "new@example.com");
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registeringTheSameEmailTwiceIs409() throws Exception {
        register("twice@example.com", PASSWORD).andExpect(status().isCreated());

        register("twice@example.com", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email already registered"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial123"})
    void weakPasswordsAreRejectedWithAFieldError(String weakPassword) throws Exception {
        register("weak@example.com", weakPassword)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message", matchesPattern("must be at least 8 characters.*")));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void unknownEmailAndWrongPasswordGetTheSameResponse() throws Exception {
        api.registerCustomer("known@example.com", PASSWORD);

        MvcResult unknown = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrongPassword = login("known@example.com", "Wr0ng!pass").andExpect(status().isUnauthorized()).andReturn();

        assertThat(withoutRequestSpecificFields(unknown)).isEqualTo(withoutRequestSpecificFields(wrongPassword));
        assertThat(unknown.getResponse().getContentType()).isEqualTo(wrongPassword.getResponse().getContentType());
        assertThat(api.read(unknown.getResponse().getContentAsString()).get("detail").asString())
                .isEqualTo("Invalid email or password");
    }

    @Test
    void loginReturnsABearerAccessTokenAndARefreshToken() throws Exception {
        api.registerCustomer("login@example.com", PASSWORD);

        String body = login("login@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken", matchesPattern("[A-Za-z0-9_-]{43}")))
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = api.read(body);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void refreshTokensAreStoredOnlyAsAHash() throws Exception {
        api.registerCustomer("hash@example.com", PASSWORD);
        Tokens tokens = api.login("hash@example.com", PASSWORD);

        Integer rawMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, tokens.refreshToken());
        Integer hashedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, OpaqueTokens.hash(tokens.refreshToken()));

        assertThat(rawMatches).isZero();
        assertThat(hashedMatches).isEqualTo(1);
    }

    @Test
    void accessTokenExpiresAfterFifteenMinutes() throws Exception {
        api.registerCustomer("expiry@example.com", PASSWORD);
        Tokens tokens = api.login("expiry@example.com", PASSWORD);

        clock.advance(Duration.ofMinutes(14));
        me(tokens.accessToken()).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(2));
        me(tokens.accessToken())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired access token"))
                .andExpect(jsonPath("$.instance").value("/api/v1/me"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedAndForgedTokensAreRejectedWithTheProblemFormat() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token")
                        .header(CorrelationIdFilter.HEADER, "corr-forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or expired access token"))
                .andExpect(jsonPath("$.correlationId").value("corr-forged"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-forged"));
    }

    @Test
    void refreshRotatesTheTokenAndRetiresTheOldOne() throws Exception {
        api.registerCustomer("rotate@example.com", PASSWORD);
        Tokens first = api.login("rotate@example.com", PASSWORD);
        clock.advance(Duration.ofMinutes(10));

        Tokens second = refreshOk(first.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        me(second.accessToken()).andExpect(status().isOk());

        Tokens third = refreshOk(second.refreshToken());
        me(third.accessToken()).andExpect(status().isOk());

        refresh(first.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingARotatedOutTokenRevokesTheWholeFamily() throws Exception {
        api.registerCustomer("reuse@example.com", PASSWORD);
        Tokens stolen = api.login("reuse@example.com", PASSWORD);
        Tokens legitimate = refreshOk(stolen.refreshToken());
        Tokens newest = refreshOk(legitimate.refreshToken());

        refresh(stolen.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));

        refresh(newest.refreshToken()).andExpect(status().isUnauthorized());
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
        assertThat(active).isZero();
        Integer families = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT family_id) FROM refresh_tokens", Integer.class);
        assertThat(families).isEqualTo(1);
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        api.registerCustomer("stale@example.com", PASSWORD);
        Tokens tokens = api.login("stale@example.com", PASSWORD);

        clock.advance(Duration.ofDays(7).plusSeconds(1));

        refresh(tokens.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        api.registerCustomer("logout@example.com", PASSWORD);
        Tokens tokens = api.login("logout@example.com", PASSWORD);

        logout(tokens.refreshToken()).andExpect(status().isNoContent());
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        logout(tokens.refreshToken()).andExpect(status().isNoContent());
        logout("never-issued").andExpect(status().isNoContent());
    }

    @Test
    void loginIsRateLimitedPerClientAndEmailAndRecoversWhenTheWindowPasses() throws Exception {
        api.registerCustomer("limited@example.com", PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            login("limited@example.com", "Wr0ng!pass").andExpect(status().isUnauthorized());
        }
        login("limited@example.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Too many login attempts, try again later"))
                .andExpect(jsonPath("$.correlationId").exists());
        login("other@example.com", PASSWORD).andExpect(status().isUnauthorized());

        clock.advance(Duration.ofMinutes(1));

        login("limited@example.com", PASSWORD).andExpect(status().isOk());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("email", email, "password", password, "fullName", "Test User"))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("email", email, "password", password))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("refreshToken", refreshToken))));
    }

    private Tokens refreshOk(String refreshToken) throws Exception {
        JsonNode node = api.read(refresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new Tokens(node.get("accessToken").asString(), node.get("refreshToken").asString());
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private JsonNode withoutRequestSpecificFields(MvcResult result) throws Exception {
        ObjectNode node = (ObjectNode) api.read(result.getResponse().getContentAsString());
        node.remove("correlationId");
        node.remove("timestamp");
        return node;
    }
}
