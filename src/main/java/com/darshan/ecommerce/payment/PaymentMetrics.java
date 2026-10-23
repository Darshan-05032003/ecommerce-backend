package com.darshan.ecommerce.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

// Tags come only from enums, so the number of series is fixed (3 methods x 3 reasons). The
// provider's decline text is free-form and stays out of the tags.
@Component
class PaymentMetrics {

    enum FailureReason {
        DECLINED,
        TIMEOUT,
        ERROR
    }

    private final Map<PaymentMethod, Map<FailureReason, Counter>> failed = new EnumMap<>(PaymentMethod.class);

    PaymentMetrics(MeterRegistry registry) {
        for (PaymentMethod method : PaymentMethod.values()) {
            Map<FailureReason, Counter> byReason = new EnumMap<>(FailureReason.class);
            for (FailureReason reason : FailureReason.values()) {
                byReason.put(reason, Counter.builder("payments.failed")
                        .description("Payment attempts that did not succeed")
                        .tag("method", method.name())
                        .tag("reason", reason.name().toLowerCase(Locale.ROOT))
                        .register(registry));
            }
            failed.put(method, byReason);
        }
    }

    void paymentFailed(PaymentMethod method, FailureReason reason) {
        failed.get(method).get(reason).increment();
    }
}
