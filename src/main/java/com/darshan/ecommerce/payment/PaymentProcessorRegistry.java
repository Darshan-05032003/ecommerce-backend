package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Spring injects every PaymentProcessor bean; adding a payment method means adding one class,
// without touching this registry or PaymentService (open/closed principle).
@Component
public class PaymentProcessorRegistry {

    static final String METHOD_UNSUPPORTED = "PAYMENT_METHOD_UNSUPPORTED";

    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    // Two processors for one method would make the choice depend on bean order, so the
    // application refuses to start instead.
    public PaymentProcessorRegistry(List<PaymentProcessor> processors) {
        for (PaymentProcessor processor : processors) {
            PaymentProcessor existing = this.processors.putIfAbsent(processor.method(), processor);
            if (existing != null) {
                throw new IllegalStateException("Two payment processors for " + processor.method() + ": "
                        + existing.getClass().getSimpleName() + " and " + processor.getClass().getSimpleName());
            }
        }
    }

    public PaymentProcessor processorFor(PaymentMethod method) {
        PaymentProcessor processor = processors.get(method);
        if (processor == null) {
            throw new BusinessRuleViolationException(METHOD_UNSUPPORTED, "Payment method " + method + " is not supported");
        }
        return processor;
    }
}
