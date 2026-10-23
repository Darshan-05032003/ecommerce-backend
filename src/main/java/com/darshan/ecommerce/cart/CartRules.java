package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.product.Product;

// The rules a cart line must satisfy after every change. Bean Validation already rejects request
// quantities outside 1..10 with a 400; these checks also cover the merged quantity after adding to
// an existing line and the product's current state, which no request annotation can see.
final class CartRules {

    static final int MIN_LINE_QUANTITY = 1;
    static final int MAX_LINE_QUANTITY = 10;

    static final String INVALID_QUANTITY = "CART_INVALID_QUANTITY";
    static final String LINE_LIMIT_EXCEEDED = "CART_LINE_LIMIT_EXCEEDED";
    static final String PRODUCT_UNAVAILABLE = "PRODUCT_UNAVAILABLE";
    static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    private CartRules() {
    }

    static void checkLine(Product product, int quantity) {
        if (quantity < MIN_LINE_QUANTITY) {
            throw new BusinessRuleViolationException(INVALID_QUANTITY,
                    "Quantity must be at least " + MIN_LINE_QUANTITY);
        }
        if (!product.isActive()) {
            throw new BusinessRuleViolationException(PRODUCT_UNAVAILABLE,
                    "Product " + product.getId() + " is no longer available");
        }
        if (quantity > MAX_LINE_QUANTITY) {
            throw new BusinessRuleViolationException(LINE_LIMIT_EXCEEDED,
                    "A cart line can hold at most " + MAX_LINE_QUANTITY + " units, this change would make it "
                            + quantity);
        }
        if (quantity > product.getStockQuantity()) {
            throw new BusinessRuleViolationException(INSUFFICIENT_STOCK,
                    "Only " + product.getStockQuantity() + " units of product " + product.getId() + " are in stock");
        }
    }
}
