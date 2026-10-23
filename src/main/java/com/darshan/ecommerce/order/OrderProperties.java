package com.darshan.ecommerce.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// idempotencyCleanupCron accepts "-", Spring's value for a disabled cron trigger, which the test
// profile uses so tests call the cleanup directly instead of racing a background run.
@ConfigurationProperties("app.orders")
@Validated
public record OrderProperties(@NotNull @DurationMin(minutes = 1) Duration idempotencyKeyRetention,
                              @NotBlank String idempotencyCleanupCron) {
}
