package com.darshan.ecommerce.payment;

import java.math.BigDecimal;

// The boundary to an external payment provider. The simulated implementation is used in
// development and tests; a real provider adapter would be a second implementation.
public interface PaymentGateway {

    // paymentId is sent as the provider-side idempotency key, so a retried charge for the same
    // attempt cannot be taken twice.
    GatewayResult charge(Long paymentId, PaymentMethod method, String instrument, BigDecimal amount);

    GatewayResult refund(String reference, BigDecimal amount);
}
