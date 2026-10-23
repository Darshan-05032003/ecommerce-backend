package com.darshan.ecommerce.common.observability;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("app.observability")
@Validated
public record ObservabilityProperties(@NotNull @DurationMin(millis = 1) @DurationMax(minutes = 1)
                                      Duration slowMethodThreshold) {
}
