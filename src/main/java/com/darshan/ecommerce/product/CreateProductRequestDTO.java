package com.darshan.ecommerce.product;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class CreateProductRequestDTO {
    @NotBlank
    @Size(max = 40)
    private String sku;

    @NotBlank
    private String name;
    
    @NotBlank
    private String description;
    
    @NotNull
    @PositiveOrZero
    private BigDecimal price;
    
    @NotNull
    @PositiveOrZero
    private Integer stockQuantity;
}
