package com.darshan.ecommerce.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @Schema(description = "The opaque refresh token from the last login or refresh",
                example = "Jx3p0Y5c2mQh9TqU7wVn1aB4dE6fG8hK0lM2nO4pQ6s") @NotBlank String refreshToken) {
}
