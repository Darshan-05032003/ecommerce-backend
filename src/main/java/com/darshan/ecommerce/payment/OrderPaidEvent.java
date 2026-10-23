package com.darshan.ecommerce.payment;

import java.math.BigDecimal;

public record OrderPaidEvent(Long orderId, String orderNumber, String customerEmail, BigDecimal amount,
                             PaymentMethod method) {
}
