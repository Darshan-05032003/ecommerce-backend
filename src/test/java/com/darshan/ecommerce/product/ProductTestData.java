package com.darshan.ecommerce.product;

import com.darshan.ecommerce.category.Category;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

// Every field has a valid default, so a test only states what matters to it, for example
// aProduct().price("5.05").stock(0).build(). SKUs are unique per JVM and match the SKU format.
public final class ProductTestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Long id;
    private String sku = "TST-ITEM-%04d".formatted(SEQUENCE.incrementAndGet());
    private String name;
    private BigDecimal price = new BigDecimal("19.99");
    private int stock = 10;
    private Category category;
    private boolean active = true;

    private ProductTestData() {
    }

    public static ProductTestData aProduct() {
        return new ProductTestData();
    }

    // Only for unit tests; persisted products get their id from the database.
    public ProductTestData id(long id) {
        this.id = id;
        return this;
    }

    public ProductTestData sku(String sku) {
        this.sku = sku;
        return this;
    }

    public ProductTestData name(String name) {
        this.name = name;
        return this;
    }

    public ProductTestData price(String price) {
        this.price = new BigDecimal(price);
        return this;
    }

    public ProductTestData stock(int stock) {
        this.stock = stock;
        return this;
    }

    // Persistence tests must pass a saved category; the default one is transient.
    public ProductTestData category(Category category) {
        this.category = category;
        return this;
    }

    public ProductTestData inactive() {
        this.active = false;
        return this;
    }

    public Product build() {
        Product product = new Product(sku, name == null ? "Product " + sku : name, "Description", price, stock,
                category == null ? new Category("General", null) : category);
        product.setActive(active);
        if (id != null) {
            ReflectionTestUtils.setField(product, "id", id);
        }
        return product;
    }
}
