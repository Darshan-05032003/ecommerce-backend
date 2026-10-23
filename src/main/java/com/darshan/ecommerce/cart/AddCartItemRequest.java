package com.darshan.ecommerce.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @Schema(example = "4") @NotNull @Positive Long productId,
        @Schema(example = "2") @NotNull @Min(CartRules.MIN_LINE_QUANTITY) @Max(CartRules.MAX_LINE_QUANTITY) Integer quantity) {
}
