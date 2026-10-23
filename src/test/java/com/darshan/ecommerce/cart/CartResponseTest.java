package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
import static org.assertj.core.api.Assertions.assertThat;

class CartResponseTest {

    private static final User USER = aUser().build();

    @Test
    void mapsALineFromTheCurrentProductState() {
        CartLineResponse line = CartLineResponse.from(item(1L, "BK-001", "19.99", 3));

        assertThat(line.productId()).isEqualTo(1L);
        assertThat(line.sku()).isEqualTo("BK-001");
        assertThat(line.name()).isEqualTo("Product BK-001");
        assertThat(line.unitPrice()).isEqualTo(new BigDecimal("19.99"));
        assertThat(line.quantity()).isEqualTo(3);
        assertThat(line.lineTotal()).isEqualTo(new BigDecimal("59.97"));
    }

    // Prices are stored as DECIMAL(10,2), so a stored price times a whole quantity never needs
    // rounding. The cases with more decimals pin down the rounding rule for amounts that do.
    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "1.005, 1, 1.00",
            "1.015, 1, 1.02",
            "0.0025, 2, 0.00",
            "0.335, 1, 0.34",
            "0.345, 1, 0.34",
            "2.50, 4, 10.00"
    })
    void roundsLineTotalsHalfEvenToTwoDecimals(String unitPrice, int quantity, String expected) {
        CartLineResponse line = CartLineResponse.from(item(1L, "RND-001", unitPrice, quantity));

        assertThat(line.lineTotal()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void totalIsTheSumOfTheRoundedLines() {
        CartResponse cart = CartResponse.of(List.of(
                CartLineResponse.from(item(1L, "A-001", "1.005", 1)),
                CartLineResponse.from(item(2L, "B-001", "1.005", 1)),
                CartLineResponse.from(item(3L, "C-001", "19.99", 2))));

        // Rounding the unrounded sum (41.990) would give 41.99, a cent more than the lines the
        // customer sees (1.00 + 1.00 + 39.98).
        assertThat(cart.total()).isEqualTo(new BigDecimal("41.98"));
        assertThat(cart.totalQuantity()).isEqualTo(4);
        assertThat(cart.lines()).hasSize(3);
    }

    @Test
    void anEmptyCartHasAZeroTotalAtScaleTwo() {
        CartResponse cart = CartResponse.of(List.of());

        assertThat(cart.lines()).isEmpty();
        assertThat(cart.totalQuantity()).isZero();
        assertThat(cart.total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void totalsCompareByValueNotByScale() {
        CartResponse cart = CartResponse.of(List.of(CartLineResponse.from(item(1L, "A-001", "5", 2))));

        assertThat(cart.total()).isEqualByComparingTo("10");
        assertThat(cart.total().compareTo(new BigDecimal("10.0"))).isZero();
        assertThat(cart.total()).isEqualTo(new BigDecimal("10.00"));
    }

    private static CartItem item(Long productId, String sku, String price, int quantity) {
        return new CartItem(USER, aProduct().id(productId).sku(sku).price(price).stock(100).build(), quantity);
    }
}
