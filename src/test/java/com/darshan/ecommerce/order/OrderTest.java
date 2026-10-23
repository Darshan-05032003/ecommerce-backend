package com.darshan.ecommerce.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void totalIsTheSumOfTheRoundedLineTotals() {
        Order order = anOrder().build();

        order.addItem(line("1.005", 1));
        order.addItem(line("1.005", 1));
        order.addItem(line("19.99", 2));

        assertThat(order.getItems()).extracting(OrderItem::getLineTotal)
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("39.98"));
        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("41.98"));
    }

    @Test
    void removingALineSubtractsItsTotal() {
        Order order = anOrder().build();
        OrderItem pen = line("5.05", 3);
        order.addItem(line("19.99", 1));
        order.addItem(pen);

        order.removeItem(pen);

        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("19.99"));
        assertThat(pen.getOrder()).isNull();
    }

    @Test
    void removingALineOfAnotherOrderChangesNothing() {
        Order order = anOrder().line(aProduct().price("19.99").build(), 1).build();
        OrderItem foreign = anOrder().line(aProduct().price("5.00").build(), 1).build().getItems().getFirst();

        order.removeItem(foreign);

        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("19.99"));
        assertThat(foreign.getOrder()).isNotNull();
    }

    @Test
    void aNewOrderStartsCreatedAndReportsThePreviousStatusOnAChange() {
        Order order = anOrder().build();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.changeStatus(OrderStatus.PAID)).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void anInvalidChangeLeavesTheStatusAlone() {
        Order order = anOrder().build();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    private static OrderItem line(String price, int quantity) {
        return new OrderItem(aProduct().price(price).build(), quantity, new BigDecimal(price));
    }
}
