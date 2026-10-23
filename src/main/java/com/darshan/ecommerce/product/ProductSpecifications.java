package com.darshan.ecommerce.product;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    // Comparing the foreign key through the association path needs no join.
    static Specification<Product> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    static Specification<Product> nameContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    static Specification<Product> inStock() {
        return (root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
    }

    // Without escaping, a search for "%" or "_" would turn into a wildcard and match everything.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
