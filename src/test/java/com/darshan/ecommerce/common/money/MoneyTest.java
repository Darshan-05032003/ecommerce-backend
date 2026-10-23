package com.darshan.ecommerce.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "1.005, 1.00",
            "1.015, 1.02",
            "1.025, 1.02",
            "0.125, 0.12",
            "0.135, 0.14",
            "1.0051, 1.01",
            "-1.005, -1.00",
            "7, 7.00"
    })
    void roundsHalvesToTheEvenNeighbourAtScaleTwo(String amount, String expected) {
        BigDecimal rounded = Money.of(new BigDecimal(amount));

        assertThat(rounded).isEqualTo(new BigDecimal(expected));
        assertThat(rounded.scale()).isEqualTo(2);
    }

    @Test
    void halfEvenDiffersFromHalfUpOnlyOnExactHalves() {
        BigDecimal half = new BigDecimal("2.005");

        assertThat(Money.of(half)).isEqualByComparingTo("2.00");
        assertThat(half.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("2.01");
    }

    @Test
    void multipliesBeforeRounding() {
        assertThat(Money.times(new BigDecimal("0.0025"), 2)).isEqualTo(new BigDecimal("0.00"));
        assertThat(Money.times(new BigDecimal("0.0025"), 3)).isEqualTo(new BigDecimal("0.01"));
        assertThat(Money.times(new BigDecimal("19.99"), 3)).isEqualTo(new BigDecimal("59.97"));
    }

    @Test
    void equalsComparesScaleWhileCompareToComparesValue() {
        assertThat(new BigDecimal("2.0")).isNotEqualTo(new BigDecimal("2.00"));
        assertThat(new BigDecimal("2.0").compareTo(new BigDecimal("2.00"))).isZero();
        assertThat(Money.ZERO).isEqualTo(new BigDecimal("0.00"));
    }
}
