package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.money.Money;
import com.darshan.ecommerce.product.Product;

import java.math.BigDecimal;

public record CartLineResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal) {

    // The price is read from the product every time: the cart shows today's price and only an
    // order freezes it.
    static CartLineResponse from(CartItem item) {
        Product product = item.getProduct();
        return new CartLineResponse(product.getId(), product.getSku(), product.getName(),
                Money.of(product.getPrice()), item.getQuantity(), Money.times(product.getPrice(), item.getQuantity()));
    }
}
