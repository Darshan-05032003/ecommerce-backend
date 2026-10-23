package com.darshan.ecommerce.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "CREATED->PAID", "CREATED->CANCELLED",
            "PAID->SHIPPED", "PAID->CANCELLED",
            "SHIPPED->DELIVERED");

    // Every ordered pair of statuses, 25 in total, including staying in the same status.
    static Stream<Arguments> allPairs() {
        return Arrays.stream(OrderStatus.values()).flatMap(from -> Arrays.stream(OrderStatus.values())
                .map(to -> Arguments.of(from, to, ALLOWED.contains(from + "->" + to))));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @MethodSource("allPairs")
    void allowsExactlyTheDocumentedTransitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);

        if (allowed) {
            assertThat(from.moveTo(to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> from.moveTo(to))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Order cannot move from " + from + " to " + to);
        }
    }
}
