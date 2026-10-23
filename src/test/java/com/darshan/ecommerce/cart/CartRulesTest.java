package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.product.Product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartRulesTest {

    @ParameterizedTest(name = "quantity {0} is accepted")
    @ValueSource(ints = {1, 5, 10})
    void acceptsQuantitiesFromOneToTen(int quantity) {
        assertThatCode(() -> CartRules.checkLine(product(true, 100), quantity)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "quantity {0} -> {1}")
    @CsvSource({
            "0, CART_INVALID_QUANTITY",
            "-1, CART_INVALID_QUANTITY",
            "11, CART_LINE_LIMIT_EXCEEDED",
            "15, CART_LINE_LIMIT_EXCEEDED"
    })
    void rejectsQuantitiesOutsideOneToTen(int quantity, String errorCode) {
        assertViolation(product(true, 100), quantity, errorCode);
    }

    // The service passes the merged quantity: what is already in the line plus what is being added.
    @ParameterizedTest(name = "{0} in the cart + {1} added")
    @CsvSource({"9, 1", "5, 5", "1, 9"})
    void acceptsAMergeThatReachesExactlyTen(int inCart, int added) {
        assertThatCode(() -> CartRules.checkLine(product(true, 100), inCart + added)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} in the cart + {1} added")
    @CsvSource({"10, 1", "6, 5", "8, 10"})
    void rejectsAMergeAboveTen(int inCart, int added) {
        assertViolation(product(true, 100), inCart + added, "CART_LINE_LIMIT_EXCEEDED");
    }

    @ParameterizedTest(name = "stock {0}, quantity {1}")
    @CsvSource({"3, 3", "3, 1", "10, 10"})
    void acceptsQuantitiesUpToTheCurrentStock(int stock, int quantity) {
        assertThatCode(() -> CartRules.checkLine(product(true, stock), quantity)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "stock {0}, quantity {1}")
    @CsvSource({"3, 4", "0, 1", "9, 10"})
    void rejectsQuantitiesAboveTheCurrentStock(int stock, int quantity) {
        assertViolation(product(true, stock), quantity, "INSUFFICIENT_STOCK");
    }

    @Test
    void rejectsAnInactiveProductEvenWhenItIsInStock() {
        assertViolation(product(false, 100), 1, "PRODUCT_UNAVAILABLE");
    }

    @Test
    void reportsTheLineLimitBeforeTheStockWhenBothAreExceeded() {
        assertViolation(product(true, 5), 11, "CART_LINE_LIMIT_EXCEEDED");
    }

    private static void assertViolation(Product product, int quantity, String errorCode) {
        assertThatThrownBy(() -> CartRules.checkLine(product, quantity))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private static Product product(boolean active, int stock) {
        Product product = new Product("CRT-001", "Cart product", "Description", new BigDecimal("10.00"), stock,
                new Category("General", null));
        ReflectionTestUtils.setField(product, "id", 7L);
        product.setActive(active);
        return product;
    }
}
