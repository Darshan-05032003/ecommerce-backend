package com.darshan.ecommerce.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("app.security.jwt")
@Validated
public record JwtProperties(
        // HS256 needs a key of at least 256 bits; the secret is used as raw UTF-8 bytes
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull @DurationMin(minutes = 1) Duration accessTokenTtl,
        @NotNull @DurationMin(minutes = 1) Duration refreshTokenTtl) {
}
