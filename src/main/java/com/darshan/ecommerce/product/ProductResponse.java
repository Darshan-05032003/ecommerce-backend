package com.darshan.ecommerce.product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(Long id,
                              String sku,
                              String name,
                              String description,
                              BigDecimal price,
                              Integer stockQuantity,
                              boolean active,
                              Integer version,
                              CategorySummary category,
                              Instant createdAt,
                              Instant updatedAt) {

    public record CategorySummary(Long id, String name) {
    }
}
