package com.darshan.ecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, PaymentMethod method, BigDecimal amount, PaymentStatus status,
                              String gatewayReference, Instant createdAt) {

    static PaymentResponse from(Payment payment, Long orderId) {
        return new PaymentResponse(payment.getId(), orderId, payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getGatewayReference(), payment.getCreatedAt());
    }
}
