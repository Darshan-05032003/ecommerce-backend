package com.darshan.ecommerce.order;

// Either the order was created by this request, or the key had already been used for the same
// payload and the stored response is returned again.
public sealed interface PlacementResult {

    OrderResponse order();

    record Created(OrderResponse order) implements PlacementResult {
    }

    record Replayed(int status, OrderResponse order) implements PlacementResult {
    }
}
