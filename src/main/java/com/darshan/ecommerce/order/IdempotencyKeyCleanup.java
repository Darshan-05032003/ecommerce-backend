package com.darshan.ecommerce.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

// A key only has to outlive the client's retry window. Deleting old rows keeps the table small;
// a retry that arrives after the cleanup is treated as a new request.
@Component
public class IdempotencyKeyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

    private final IdempotencyKeyStore store;
    private final OrderProperties properties;
    private final Clock clock;

    public IdempotencyKeyCleanup(IdempotencyKeyStore store, OrderProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.orders.idempotency-cleanup-cron}")
    public int deleteExpiredKeys() {
        Instant cutoff = clock.instant().minus(properties.idempotencyKeyRetention());
        int deleted = store.deleteCreatedBefore(cutoff);
        log.info("Deleted {} idempotency keys created before {}", deleted, cutoff);
        return deleted;
    }
}
