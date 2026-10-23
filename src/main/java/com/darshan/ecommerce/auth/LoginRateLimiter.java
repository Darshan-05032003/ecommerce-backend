package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.config.RateLimitProperties;
import com.darshan.ecommerce.common.exception.TooManyRequestsException;
import com.darshan.ecommerce.common.logging.LogMasking;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// One token bucket per client IP + email. Keying on both means an attacker cannot lock a
// victim out from another address, and a single address cannot try many accounts freely.
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    // Above this many tracked keys, buckets that have refilled completely are dropped: a full
    // bucket carries no state, so forgetting it changes nothing for that client.
    static final int PRUNE_THRESHOLD = 10_000;

    private final RateLimitProperties.Login settings;
    private final TimeMeter timeMeter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(RateLimitProperties properties, Clock clock) {
        this.settings = properties.login();
        this.timeMeter = new ClockTimeMeter(clock);
    }

    public void check(String clientIp, String email) {
        Bucket bucket = buckets.computeIfAbsent(key(clientIp, email), key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return;
        }
        Duration retryAfter = Duration.ofNanos(probe.getNanosToWaitForRefill());
        log.warn("Login rate limit exceeded for {} from {}", LogMasking.email(email), clientIp);
        throw new TooManyRequestsException("Too many login attempts, try again later", retryAfter);
    }

    private Bucket newBucket() {
        pruneIfLarge();
        Bandwidth limit = Bandwidth.builder()
                .capacity(settings.attempts())
                .refillIntervally(settings.attempts(), settings.window())
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private void pruneIfLarge() {
        if (buckets.size() >= PRUNE_THRESHOLD) {
            buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= settings.attempts());
        }
    }

    int trackedKeys() {
        return buckets.size();
    }

    private static String key(String clientIp, String email) {
        return clientIp + "|" + email.trim().toLowerCase(Locale.ROOT);
    }

    // Bucket4j measures time itself by default; reading the application Clock instead keeps
    // the limiter consistent with the rest of the code and lets tests move time forward.
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
