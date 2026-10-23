package com.darshan.ecommerce.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
        @Schema(example = "221B Baker Street, London NW1 6XE") @NotBlank @Size(max = 255) String shippingAddress) {
}
