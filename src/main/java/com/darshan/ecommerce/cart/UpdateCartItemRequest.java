package com.darshan.ecommerce.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        @NotNull @Min(CartRules.MIN_LINE_QUANTITY) @Max(CartRules.MAX_LINE_QUANTITY) Integer quantity) {
}
