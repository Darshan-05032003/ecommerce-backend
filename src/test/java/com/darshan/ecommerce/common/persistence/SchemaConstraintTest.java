package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PersistenceTest
class SchemaConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(user("dup@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSku() {
        productRepository.saveAndFlush(product("SKU-1", "First"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product("SKU-1", "Second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativePrice() {
        Product product = product("SKU-NEG", "Negative");
        product.setPrice(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeStock() {
        Product product = product("SKU-STOCK", "Stock");
        product.setStockQuantity(-1);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsZeroQuantityCartLine() {
        User user = userRepository.saveAndFlush(user("zero@example.com"));
        Product product = productRepository.saveAndFlush(product("SKU-ZERO", "Zero"));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(user, product, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondCartLineForTheSameProduct() {
        User user = userRepository.saveAndFlush(user("twice@example.com"));
        Product product = productRepository.saveAndFlush(product("SKU-TWICE", "Twice"));
        cartItemRepository.saveAndFlush(new CartItem(user, product, 1));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(user, product, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static User user(String email) {
        return new User(email, "{noop}irrelevant", "Test User", Set.of());
    }

    private Product product(String sku, String name) {
        Category category = categoryRepository.findByName("Constraints")
                .orElseGet(() -> categoryRepository.save(new Category("Constraints", null)));
        return new Product(sku, name, "Description", new BigDecimal("9.99"), 5, category);
    }
}
