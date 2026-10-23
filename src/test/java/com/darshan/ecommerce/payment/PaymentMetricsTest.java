package com.darshan.ecommerce.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentMetrics metrics = new PaymentMetrics(registry);

    @Test
    void registersOneZeroCounterPerMethodAndReason() {
        assertThat(registry.get("payments.failed").counters()).hasSize(9)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    void incrementsOnlyTheMatchingSeries() {
        metrics.paymentFailed(PaymentMethod.CARD, PaymentMetrics.FailureReason.DECLINED);
        metrics.paymentFailed(PaymentMethod.CARD, PaymentMetrics.FailureReason.DECLINED);
        metrics.paymentFailed(PaymentMethod.UPI, PaymentMetrics.FailureReason.TIMEOUT);

        assertThat(count("CARD", "declined")).isEqualTo(2);
        assertThat(count("UPI", "timeout")).isEqualTo(1);
        assertThat(registry.get("payments.failed").counters().stream().mapToDouble(Counter::count).sum()).isEqualTo(3);
    }

    private double count(String method, String reason) {
        return registry.get("payments.failed").tag("method", method).tag("reason", reason).counter().count();
    }
}
