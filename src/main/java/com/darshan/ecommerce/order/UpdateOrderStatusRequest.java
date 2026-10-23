package com.darshan.ecommerce.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status, @Size(max = 255) String reason) {
}
