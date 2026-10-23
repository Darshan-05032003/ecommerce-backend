package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.InvalidRequestException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

// Every meter is registered up front, so a scrape shows 0 before the first order instead of a
// missing series, and rate() in Prometheus has a starting point.
@Component
class OrderMetrics {

    enum Outcome {
        // A new order was committed.
        CREATED,
        // A retry with a used Idempotency-Key got the stored response.
        REPLAYED,
        // The request broke a business rule: empty cart, out of stock, reused key.
        REJECTED,
        // Anything unexpected.
        FAILED;

        private final String tag = name().toLowerCase(Locale.ROOT);
    }

    private final MeterRegistry registry;
    private final Counter placed;
    private final Map<Outcome, Timer> placementTimers = new EnumMap<>(Outcome.class);

    OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.placed = Counter.builder("orders.placed")
                .description("Orders created and committed")
                .register(registry);
        for (Outcome outcome : Outcome.values()) {
            placementTimers.put(outcome, Timer.builder("orders.placement")
                    .description("Time taken to handle an order placement request")
                    .tag("outcome", outcome.tag)
                    .register(registry));
        }
    }

    Timer.Sample startPlacement() {
        return Timer.start(registry);
    }

    void placementFinished(Timer.Sample sample, PlacementResult result) {
        Outcome outcome = switch (result) {
            case PlacementResult.Created created -> {
                placed.increment();
                yield Outcome.CREATED;
            }
            case PlacementResult.Replayed replayed -> Outcome.REPLAYED;
        };
        sample.stop(placementTimers.get(outcome));
    }

    void placementFailed(Timer.Sample sample, RuntimeException ex) {
        sample.stop(placementTimers.get(isRejection(ex) ? Outcome.REJECTED : Outcome.FAILED));
    }

    private static boolean isRejection(RuntimeException ex) {
        return ex instanceof BusinessRuleViolationException
                || ex instanceof InvalidRequestException
                || ex instanceof ConflictException
                || ex instanceof ResourceNotFoundException;
    }
}
