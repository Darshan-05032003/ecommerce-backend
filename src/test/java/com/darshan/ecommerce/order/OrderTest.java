package com.darshan.ecommerce.order;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.product.Product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final Category category = new Category("Books", null);

    @Test
    void totalIsTheSumOfTheRoundedLineTotals() {
        Order order = new Order("ORD-2026-000001", null, "1 Main Street");

        order.addItem(new OrderItem(product("1.005"), 1, new BigDecimal("1.005")));
        order.addItem(new OrderItem(product("1.005"), 1, new BigDecimal("1.005")));
        order.addItem(new OrderItem(product("19.99"), 2, new BigDecimal("19.99")));

        assertThat(order.getItems()).extracting(OrderItem::getLineTotal)
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("39.98"));
        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("41.98"));
    }

    @Test
    void removingALineSubtractsItsTotal() {
        Order order = new Order("ORD-2026-000002", null, "1 Main Street");
        OrderItem pen = new OrderItem(product("5.05"), 3, new BigDecimal("5.05"));
        order.addItem(new OrderItem(product("19.99"), 1, new BigDecimal("19.99")));
        order.addItem(pen);

        order.removeItem(pen);

        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("19.99"));
        assertThat(pen.getOrder()).isNull();
    }

    @Test
    void aNewOrderStartsCreatedAndReportsThePreviousStatusOnAChange() {
        Order order = new Order("ORD-2026-000003", null, "1 Main Street");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.changeStatus(OrderStatus.PAID)).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void anInvalidChangeLeavesTheStatusAlone() {
        Order order = new Order("ORD-2026-000004", null, "1 Main Street");

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    private Product product(String price) {
        return new Product("BK-001", "Book", "Book", new BigDecimal(price), 10, category);
    }
}
