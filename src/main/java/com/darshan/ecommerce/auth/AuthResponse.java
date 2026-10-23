package com.darshan.ecommerce.auth;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public AuthResponse(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
