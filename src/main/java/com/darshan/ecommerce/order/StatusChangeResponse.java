package com.darshan.ecommerce.order;

import java.time.Instant;

public record StatusChangeResponse(OrderStatus from, OrderStatus to, String changedBy, Instant changedAt,
                                   String reason) {

    static StatusChangeResponse from(OrderStatusHistory entry) {
        return new StatusChangeResponse(entry.getFromStatus(), entry.getToStatus(), entry.getChangedBy(),
                entry.getChangedAt(), entry.getReason());
    }
}
