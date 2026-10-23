package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.exception.ProblemDetailFactory;
import com.darshan.ecommerce.common.exception.TooManyRequestsException;
import com.darshan.ecommerce.user.RegisterUserRequest;
import com.darshan.ecommerce.user.UserResponse;
import com.darshan.ecommerce.user.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boot's default chain applies in the slice (the application's SecurityConfig is not loaded), so
// requests run as a mock user with a CSRF token; the open access to /auth is covered by
// SecurityMatrixTest.
@WebMvcTest(controllers = AuthController.class)
@Import({ClockConfig.class, ProblemDetailFactory.class})
@WithMockUser
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void registrationIs201AndNeverEchoesThePassword() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest("alice@example.com", "Str0ng!pw", "Alice");
        when(userService.register(request))
                .thenReturn(new UserResponse(1L, "alice@example.com", "Alice", Set.of("CUSTOMER"), null));

        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"Str0ng!pw\",\"fullName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "{\"email\":\"alice@example.com\",\"password\":\"password\",\"fullName\":\"Alice\"} | password",
            "{\"email\":\"not-an-email\",\"password\":\"Str0ng!pw\",\"fullName\":\"Alice\"}     | email",
            "{\"email\":\"alice@example.com\",\"password\":\"Str0ng!pw\",\"fullName\":\"\"}     | fullName"
    })
    void anInvalidRegistrationNamesTheField(String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));

        verifyNoInteractions(userService);
    }

    @Test
    void loginIsCheckedAgainstTheRateLimitWithTheClientAddressAndEmail() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("access", "refresh", 900));

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"Str0ng!pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        verify(loginRateLimiter).check("203.0.113.9", "alice@example.com");
    }

    @Test
    void aRateLimitedLoginIs429WithRetryAfterAndNeverChecksThePassword() throws Exception {
        doThrow(new TooManyRequestsException("Too many login attempts, try again later", Duration.ofMillis(41_200)))
                .when(loginRateLimiter).check(any(), any());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"guess\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"));

        verifyNoInteractions(authService);
    }
}
