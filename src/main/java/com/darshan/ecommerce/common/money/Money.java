package com.darshan.ecommerce.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Every amount the API returns goes through here, so scale and rounding are decided in one place.
// HALF_EVEN (banker's rounding) sends exact halves to the even neighbour, so over many roundings
// the errors cancel out instead of always pushing totals up as HALF_UP does.
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal times(BigDecimal unitPrice, int quantity) {
        return of(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
}
