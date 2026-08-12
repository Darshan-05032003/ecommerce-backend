package com.example.ecommerce.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductResponseDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
}
