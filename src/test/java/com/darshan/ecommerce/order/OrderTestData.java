package com.darshan.ecommerce.order;

import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserTestData;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Builds an order the way the application does: lines at the product's current price, and a
// target status reached through the real lifecycle (CREATED -> PAID -> SHIPPED ...), so a test
// can never create an order in a state the state machine would not allow.
public final class OrderTestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Long id;
    // A prefix the real generator never produces, so built orders cannot collide with placed ones.
    private final String orderNumber = "ORD-TEST-%06d".formatted(SEQUENCE.incrementAndGet());
    private User user;
    private final String shippingAddress = "1 Main Street, Pune";
    private final List<OrderItem> items = new ArrayList<>();
    private OrderStatus status = OrderStatus.CREATED;

    private OrderTestData() {
    }

    public static OrderTestData anOrder() {
        return new OrderTestData();
    }

    // Only for unit tests; persisted orders get their id from the database.
    public OrderTestData id(long id) {
        this.id = id;
        return this;
    }

    // Persistence tests must pass a saved user; the default one is transient.
    public OrderTestData user(User user) {
        this.user = user;
        return this;
    }

    public OrderTestData line(Product product, int quantity) {
        items.add(new OrderItem(product, quantity, product.getPrice()));
        return this;
    }

    public OrderTestData status(OrderStatus status) {
        this.status = status;
        return this;
    }

    public Order build() {
        Order order = new Order(orderNumber, user == null ? UserTestData.aUser().build() : user, shippingAddress);
        items.forEach(order::addItem);
        pathTo(status).forEach(order::changeStatus);
        if (id != null) {
            ReflectionTestUtils.setField(order, "id", id);
        }
        return order;
    }

    private static List<OrderStatus> pathTo(OrderStatus target) {
        return switch (target) {
            case CREATED -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
        };
    }
}
