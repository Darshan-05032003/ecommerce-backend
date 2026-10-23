package com.darshan.ecommerce.order;

import java.math.BigDecimal;

// Domain events carry everything a listener needs, so an asynchronous listener never has to load
// entities outside the transaction that published the event.
public record OrderPlacedEvent(Long orderId, String orderNumber, String customerEmail, BigDecimal totalAmount) {
}
