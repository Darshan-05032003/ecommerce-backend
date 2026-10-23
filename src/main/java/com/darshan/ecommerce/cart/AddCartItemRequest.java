package com.darshan.ecommerce.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(CartRules.MIN_LINE_QUANTITY) @Max(CartRules.MAX_LINE_QUANTITY) Integer quantity) {
}
