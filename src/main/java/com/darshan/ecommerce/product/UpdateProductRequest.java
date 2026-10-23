package com.darshan.ecommerce.product;

import com.darshan.ecommerce.common.validation.ValidSku;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// The client sends back the version it read; an outdated version means somebody else saved first.
public record UpdateProductRequest(@NotBlank @ValidSku String sku,
                                   @NotBlank @Size(max = 150) String name,
                                   @NotBlank @Size(max = 500) String description,
                                   @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
                                   @NotNull @PositiveOrZero Integer stockQuantity,
                                   @NotNull Long categoryId,
                                   @NotNull Boolean active,
                                   @NotNull Integer version) {
}
