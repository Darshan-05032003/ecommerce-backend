package com.darshan.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String orderNumber, Long userId, OrderStatus status, BigDecimal totalAmount,
                            String shippingAddress, Instant createdAt, List<OrderLineResponse> items) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getUser().getId(), order.getStatus(),
                order.getTotalAmount(), order.getShippingAddress(), order.getCreatedAt(),
                order.getItems().stream().map(OrderLineResponse::from).toList());
    }
}
