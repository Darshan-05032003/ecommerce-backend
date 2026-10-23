package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ConflictException;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OrderMetrics metrics = new OrderMetrics(registry);

    @Test
    void registersEveryMeterAtZeroBeforeTheFirstOrder() {
        assertThat(registry.get("orders.placed").counter().count()).isZero();
        assertThat(registry.get("orders.placement").timers())
                .extracting(timer -> timer.getId().getTag("outcome"))
                .containsExactlyInAnyOrder("created", "replayed", "rejected", "failed");
        assertThat(registry.get("orders.placement").timers()).allSatisfy(timer -> assertThat(timer.count()).isZero());
    }

    @Test
    void aCreatedOrderIsCountedAndAReplayIsNot() {
        metrics.placementFinished(metrics.startPlacement(), new PlacementResult.Created(order()));
        metrics.placementFinished(metrics.startPlacement(), new PlacementResult.Replayed(201, order()));

        assertThat(registry.get("orders.placed").counter().count()).isEqualTo(1);
        assertThat(timer("created").count()).isEqualTo(1);
        assertThat(timer("replayed").count()).isEqualTo(1);
    }

    @Test
    void businessRuleFailuresAreRejectionsAndEverythingElseIsAFailure() {
        metrics.placementFailed(metrics.startPlacement(), new BusinessRuleViolationException("OUT_OF_STOCK", "x"));
        metrics.placementFailed(metrics.startPlacement(), new ConflictException("x"));
        metrics.placementFailed(metrics.startPlacement(), new DuplicateKeyException("x"));

        assertThat(timer("rejected").count()).isEqualTo(2);
        assertThat(timer("failed").count()).isEqualTo(1);
        assertThat(registry.get("orders.placed").counter().count()).isZero();
    }

    private Timer timer(String outcome) {
        return registry.get("orders.placement").tag("outcome", outcome).timer();
    }

    private static OrderResponse order() {
        return new OrderResponse(1L, "ORD-2026-000001", 1L, OrderStatus.CREATED, null, "1 Road", null, List.of());
    }
}
