package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.exception.ConflictException;

// A conflict with the current state of the order (409): the same request may be valid for an
// order in another status, so it is not a malformed request.
public class InvalidOrderStateException extends ConflictException {

    public InvalidOrderStateException(OrderStatus from, OrderStatus to) {
        super("Order cannot move from " + from + " to " + to);
    }
}
