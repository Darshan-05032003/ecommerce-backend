package com.darshan.ecommerce.order;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("app.orders")
@Validated
public record OrderProperties(@NotNull @DurationMin(minutes = 1) Duration idempotencyKeyRetention) {
}
