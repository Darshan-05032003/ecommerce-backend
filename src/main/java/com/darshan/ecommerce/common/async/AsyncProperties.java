package com.darshan.ecommerce.common.async;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// The executor for @Async work. Threads above coreSize are only started once the queue is full,
// so maxSize matters only under a backlog; queueCapacity bounds the memory a backlog can take.
@ConfigurationProperties("app.async")
@Validated
public record AsyncProperties(@Min(1) @Max(64) int coreSize,
                              @Min(1) @Max(64) int maxSize,
                              @Min(1) @Max(10_000) int queueCapacity,
                              @NotBlank String threadNamePrefix,
                              @NotNull RejectionPolicy rejectionPolicy,
                              @NotNull Duration shutdownTimeout) {

    public enum RejectionPolicy {
        // The submitting thread runs the task itself: nothing is lost and the caller is slowed down.
        CALLER_RUNS,
        // The task is refused with an exception that the submitting thread sees.
        ABORT,
        // The task is dropped and a warning is logged.
        DISCARD
    }

    @AssertTrue(message = "maxSize must not be smaller than coreSize")
    public boolean isMaxSizeAtLeastCoreSize() {
        return maxSize >= coreSize;
    }
}
