package com.darshan.ecommerce.order;

// The order lifecycle. Each status knows which statuses may follow it, so an illegal move is
// rejected in one place no matter which endpoint asked for it.
public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canMoveTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public OrderStatus moveTo(OrderStatus target) {
        if (!canMoveTo(target)) {
            throw new InvalidOrderStateException(this, target);
        }
        return target;
    }
}
