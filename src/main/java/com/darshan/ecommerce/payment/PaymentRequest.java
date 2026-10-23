package com.darshan.ecommerce.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// paymentToken: the card token issued by the provider's client SDK (the API never sees card
// numbers). upiId: the customer's UPI address, such as name@bank. COD needs neither.
public record PaymentRequest(@NotNull PaymentMethod method,
                             @Size(max = 100) String paymentToken,
                             @Size(max = 100) String upiId) {
}
