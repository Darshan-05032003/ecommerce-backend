package com.darshan.ecommerce.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(@NotBlank @Size(max = 255) String shippingAddress) {
}
