package com.darshan.ecommerce.payment;

// One implementation per payment method (Strategy). Each knows what its method needs from the
// customer and how money moves for it; PaymentService does not branch on the method.
public interface PaymentProcessor {

    PaymentMethod method();

    // Rejects a request that lacks what this method needs, before any attempt is recorded.
    // Cash on delivery needs nothing from the customer, hence the default.
    default void validate(PaymentRequest request) {
    }

    GatewayResult charge(Payment payment, PaymentRequest request);

    GatewayResult refund(Payment payment);
}
