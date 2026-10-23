package com.darshan.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(Long id, String orderNumber, Long userId, OrderStatus status,
                                  BigDecimal totalAmount, String shippingAddress, Instant createdAt,
                                  List<OrderLineResponse> items, List<StatusChangeResponse> history) {

    static OrderDetailResponse from(Order order, List<OrderStatusHistory> history) {
        OrderResponse summary = OrderResponse.from(order);
        return new OrderDetailResponse(summary.id(), summary.orderNumber(), summary.userId(), summary.status(),
                summary.totalAmount(), summary.shippingAddress(), summary.createdAt(), summary.items(),
                history.stream().map(StatusChangeResponse::from).toList());
    }
}
