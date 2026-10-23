package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;
import com.darshan.ecommerce.user.RegisterUserRequest;
import com.darshan.ecommerce.user.UserResponse;
import com.darshan.ecommerce.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = ApiTags.AUTH)
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, UserService userService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.userService = userService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer account")
    @ApiResponse(responseCode = "201", description = "Account created with the CUSTOMER role")
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for an access token and a refresh token")
    @ApiResponse(responseCode = "200", description = "Tokens issued")
    @ApiResponse(responseCode = "401", ref = ApiErrorResponses.UNAUTHORIZED)
    @ApiResponse(responseCode = "429", ref = ApiErrorResponses.TOO_MANY_REQUESTS)
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        loginRateLimiter.check(http.getRemoteAddr(), request.email());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token")
    @ApiResponse(responseCode = "200", description = "New access token and new refresh token; the old one is retired")
    @ApiResponse(responseCode = "401", ref = ApiErrorResponses.UNAUTHORIZED)
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and every token derived from the same login")
    @ApiResponse(responseCode = "204", description = "Revoked, or the token was already unusable")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
