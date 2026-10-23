package com.darshan.ecommerce.order;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.payment.PaymentRefunds;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 50L;
    private static final Instant NOW = Instant.parse("2026-03-15T10:15:30Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentRefunds paymentRefunds;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher events;

    private OrderService orderService;
    private final User customer = aUser().id(USER_ID).email("alice@example.com").build();
    private final Product keyboard = aProduct().id(30L).price("45.00").build();
    private final Product mouse = aProduct().id(10L).price("20.00").build();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, cartItemRepository, userRepository,
                productService, paymentRefunds, () -> Optional.of("alice@example.com"), new MutableClock(NOW),
                entityManager, events);
    }

    // Two orders that share products must lock the product rows in the same order, otherwise each
    // could hold one row and wait for the other's: a deadlock.
    @Test
    void placingAnOrderReservesStockInProductIdOrderAndClearsTheOrderedLines() {
        when(cartItemRepository.findCartWithProducts(USER_ID))
                .thenReturn(List.of(cartLine(101L, keyboard, 1), cartLine(102L, mouse, 2)));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(customer);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse order = orderService.create(USER_ID, "ORD-2026-000042", new PlaceOrderRequest("  1 Main Street  "));

        InOrder reservations = inOrder(productService);
        reservations.verify(productService).reserveStock(10L, 2);
        reservations.verify(productService).reserveStock(30L, 1);
        verify(cartItemRepository).deleteAllByIdInBatch(List.of(102L, 101L));
        assertThat(order.totalAmount()).isEqualTo(new BigDecimal("85.00"));
        assertThat(order.shippingAddress()).isEqualTo("1 Main Street");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);

        OrderStatusHistory history = savedHistory();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.getChangedBy()).isEqualTo("alice@example.com");
        assertThat(history.getChangedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new OrderPlacedEvent(null, "ORD-2026-000042", "alice@example.com",
                new BigDecimal("85.00")));
    }

    @Test
    void anEmptyCartIsRejectedBeforeAnyStockIsTouched() {
        when(cartItemRepository.findCartWithProducts(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.create(USER_ID, "ORD-2026-000043", new PlaceOrderRequest("1 Main Street")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(OrderService.CART_EMPTY);

        verifyNoInteractions(productService, events);
        verify(orderRepository, never()).save(any());
    }

    // The versioned UPDATE is flushed first, so a concurrent change fails before any stock moves.
    @Test
    void cancellingAPaidOrderFlushesFirstThenReleasesStockAndRefunds() {
        Order order = anOrder().id(ORDER_ID).user(customer).line(keyboard, 1).line(mouse, 3)
                .status(OrderStatus.PAID).build();
        when(orderRepository.findWithItemsByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRefunds.refundFor(ORDER_ID)).thenReturn(true);

        OrderDetailResponse cancelled = orderService.cancelForUser(USER_ID, ORDER_ID);

        InOrder steps = inOrder(orderRepository, productService, paymentRefunds);
        steps.verify(orderRepository).flush();
        steps.verify(productService).releaseStock(10L, 3);
        steps.verify(productService).releaseStock(30L, 1);
        steps.verify(paymentRefunds).refundFor(ORDER_ID);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedHistory().getFromStatus()).isEqualTo(OrderStatus.PAID);
        verify(events).publishEvent(new OrderCancelledEvent(ORDER_ID, order.getOrderNumber(), "alice@example.com",
                new BigDecimal("105.00"), true));
    }

    @Test
    void cancellingAnUnpaidOrderDoesNotAskForARefund() {
        Order order = anOrder().id(ORDER_ID).user(customer).line(mouse, 1).build();
        when(orderRepository.findWithItemsByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        orderService.cancelForUser(USER_ID, ORDER_ID);

        verifyNoInteractions(paymentRefunds);
        verify(productService).releaseStock(10L, 1);
        verify(events).publishEvent(new OrderCancelledEvent(ORDER_ID, order.getOrderNumber(), "alice@example.com",
                new BigDecimal("20.00"), false));
    }

    @Test
    void anIllegalStatusChangeWritesNothing() {
        Order order = anOrder().id(ORDER_ID).line(mouse, 1).status(OrderStatus.SHIPPED).build();
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(ORDER_ID,
                new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Customer called")))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(orderRepository, never()).flush();
        verify(productService, never()).releaseStock(anyLong(), anyInt());
        verifyNoInteractions(historyRepository, events);
    }

    @Test
    void anotherUsersOrderIsReportedAsNotFound() {
        when(orderRepository.findWithItemsByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getForUser(USER_ID, ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found with id " + ORDER_ID);
    }

    @Test
    void listingTheOrdersOfAnUnknownUserIsNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.listAnyUsersOrders(99L, Pageable.ofSize(20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id 99");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void onlyACreatedOrderIsPayable() {
        Order paid = anOrder().id(ORDER_ID).line(mouse, 1).status(OrderStatus.PAID).build();
        when(orderRepository.findWithItemsByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> orderService.payableOrder(USER_ID, ORDER_ID))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    private OrderStatusHistory savedHistory() {
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        return history.getValue();
    }

    private CartItem cartLine(Long id, Product product, int quantity) {
        CartItem line = new CartItem(customer, product, quantity);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }
}
