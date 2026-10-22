package com.darshan.ecommerce.order;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class OrderResponseDTO {
    private Long id;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;
    private List<OrderItemResponseDTO> items;
}
