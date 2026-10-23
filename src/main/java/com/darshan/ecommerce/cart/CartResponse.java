package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.money.Money;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartLineResponse> lines, int totalQuantity, BigDecimal total) {

    // Each line is rounded first and the total is the sum of the rounded lines, so the total
    // always equals what a customer gets by adding up the lines they see.
    static CartResponse of(List<CartLineResponse> lines) {
        BigDecimal total = lines.stream()
                .map(CartLineResponse::lineTotal)
                .reduce(Money.ZERO, BigDecimal::add);
        int totalQuantity = lines.stream().mapToInt(CartLineResponse::quantity).sum();
        return new CartResponse(List.copyOf(lines), totalQuantity, Money.of(total));
    }
}
