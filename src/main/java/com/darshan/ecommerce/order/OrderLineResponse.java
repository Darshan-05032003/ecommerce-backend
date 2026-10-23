package com.darshan.ecommerce.order;

import com.darshan.ecommerce.product.Product;

import java.math.BigDecimal;

public record OrderLineResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                                BigDecimal lineTotal) {

    static OrderLineResponse from(OrderItem item) {
        Product product = item.getProduct();
        return new OrderLineResponse(product.getId(), product.getSku(), product.getName(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal());
    }
}
