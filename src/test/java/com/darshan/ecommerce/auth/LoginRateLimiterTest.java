package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.common.config.RateLimitProperties;
import com.darshan.ecommerce.common.exception.TooManyRequestsException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-15T10:15:30Z"));
    private final LoginRateLimiter limiter =
            new LoginRateLimiter(new RateLimitProperties(new RateLimitProperties.Login(5, WINDOW)), clock);

    @Test
    void allowsTheConfiguredNumberOfAttemptsThenBlocksWithARetryAfter() {
        for (int i = 0; i < 5; i++) {
            limiter.check("10.0.0.1", "alice@example.com");
        }
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "alice@example.com"))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void refillsWhenTheWindowHasPassedWithoutSleeping() {
        for (int i = 0; i < 5; i++) {
            limiter.check("10.0.0.1", "alice@example.com");
        }
        assertThatThrownBy(() -> limiter.check("10.0.0.1", "alice@example.com"))
                .isInstanceOf(TooManyRequestsException.class);

        clock.advance(WINDOW);

        assertThatCode(() -> limiter.check("10.0.0.1", "alice@example.com")).doesNotThrowAnyException();
    }

    @Test
    void keysOnClientAddressAndEmailTogether() {
        for (int i = 0; i < 5; i++) {
            limiter.check("10.0.0.1", "alice@example.com");
        }

        assertThatCode(() -> limiter.check("10.0.0.2", "alice@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("10.0.0.1", "bob@example.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("10.0.0.1", " Alice@Example.com "))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void forgetsIdleClientsOnceTheMapGrowsLarge() {
        for (int i = 0; i < LoginRateLimiter.PRUNE_THRESHOLD; i++) {
            limiter.check("10.0.0.1", "user" + i + "@example.com");
        }
        for (int i = 0; i < 4; i++) {
            limiter.check("10.0.0.1", "busy@example.com");
        }
        clock.advance(WINDOW);
        limiter.check("10.0.0.1", "trigger@example.com");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
