package com.darshan.ecommerce.product;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record ProductFilter(Long categoryId,
                            @DecimalMin("0.00") BigDecimal minPrice,
                            @DecimalMin("0.00") BigDecimal maxPrice,
                            @Size(max = 150) String q,
                            Boolean inStock) {

    @AssertTrue(message = "minPrice must not be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public Specification<Product> toSpecification() {
        List<Specification<Product>> specifications = new ArrayList<>();
        if (categoryId != null) {
            specifications.add(ProductSpecifications.inCategory(categoryId));
        }
        if (minPrice != null) {
            specifications.add(ProductSpecifications.priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            specifications.add(ProductSpecifications.priceAtMost(maxPrice));
        }
        if (q != null && !q.isBlank()) {
            specifications.add(ProductSpecifications.nameContains(q.trim()));
        }
        if (Boolean.TRUE.equals(inStock)) {
            specifications.add(ProductSpecifications.inStock());
        }
        return Specification.allOf(specifications);
    }
}
