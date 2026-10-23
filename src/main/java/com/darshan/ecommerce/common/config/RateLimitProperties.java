package com.darshan.ecommerce.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit")
@Validated
public record RateLimitProperties(@NotNull @Valid Login login) {

    public record Login(
            @Positive int attempts,
            @NotNull @DurationMin(seconds = 1) Duration window) {
    }
}
