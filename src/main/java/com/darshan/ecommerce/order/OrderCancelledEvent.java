package com.darshan.ecommerce.order;

import java.math.BigDecimal;

public record OrderCancelledEvent(Long orderId, String orderNumber, String customerEmail, BigDecimal totalAmount,
                                  boolean refunded) {
}
