package com.darshan.ecommerce.order;

import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.common.web.PageResponse;
import com.darshan.ecommerce.payment.PaymentRefunds;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    static final String CART_EMPTY = "CART_EMPTY";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final PaymentRefunds paymentRefunds;
    private final AuditorAware<String> auditor;
    private final Clock clock;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository historyRepository,
                        CartItemRepository cartItemRepository,
                        UserRepository userRepository,
                        ProductService productService,
                        PaymentRefunds paymentRefunds,
                        AuditorAware<String> auditor,
                        Clock clock,
                        EntityManager entityManager,
                        ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.paymentRefunds = paymentRefunds;
        this.auditor = auditor;
        this.clock = clock;
        this.entityManager = entityManager;
        this.events = events;
    }

    // Runs inside the placement transaction opened by OrderPlacementService (MANDATORY): every line
    // reserves stock, the order is saved and the ordered cart lines are removed, or nothing changes.
    // Any exception thrown here is unchecked, so the transaction rolls back by default.
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderResponse create(Long userId, String orderNumber, PlaceOrderRequest request) {
        // Products are joined into the cart query, and the lines are reserved in product id order so
        // two orders touching the same products lock the rows in the same order and cannot deadlock.
        List<CartItem> cartItems = cartItemRepository.findCartWithProducts(userId).stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        if (cartItems.isEmpty()) {
            throw new BusinessRuleViolationException(CART_EMPTY, "Cart is empty");
        }

        Order order = new Order(orderNumber, userRepository.getReferenceById(userId), request.shippingAddress().strip());
        for (CartItem line : cartItems) {
            productService.reserveStock(line.getProduct().getId(), line.getQuantity());
            order.addItem(new OrderItem(line.getProduct(), line.getQuantity(), line.getProduct().getPrice()));
        }

        Order saved = orderRepository.save(order);
        recordStatus(saved, null, "Order placed");
        cartItemRepository.deleteAllByIdInBatch(cartItems.stream().map(CartItem::getId).toList());
        events.publishEvent(new OrderPlacedEvent(saved.getId(), saved.getOrderNumber(), saved.getUser().getEmail(),
                saved.getTotalAmount()));
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForUser(Long userId, Pageable pageable) {
        return withItems(orderRepository.findIdsByUserId(userId, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAnyUsersOrders(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id " + userId);
        }
        return withItems(orderRepository.findIdsByUserId(userId, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAll(OrderStatus status, Pageable pageable) {
        return withItems(orderRepository.findIdsByStatus(status, pageable));
    }

    // Another user's order answers 404, exactly like an order that does not exist, so ids cannot
    // be probed to learn which orders exist.
    @Transactional(readOnly = true)
    public OrderDetailResponse getForUser(Long userId, Long orderId) {
        return detail(ownedOrder(userId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse get(Long orderId) {
        return detail(anyOrder(orderId));
    }

    public OrderDetailResponse cancelForUser(Long userId, Long orderId) {
        return changeStatus(ownedOrder(userId, orderId), OrderStatus.CANCELLED, "Cancelled by customer");
    }

    public OrderDetailResponse changeStatus(Long orderId, UpdateOrderStatusRequest request) {
        return changeStatus(anyOrder(orderId), request.status(), request.reason());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Order payableOrder(Long userId, Long orderId) {
        Order order = ownedOrder(userId, orderId);
        if (!order.getStatus().canMoveTo(OrderStatus.PAID)) {
            throw new InvalidOrderStateException(order.getStatus(), OrderStatus.PAID);
        }
        return order;
    }

    // SELECT ... FOR UPDATE with a version check: fails with 409 if the order changed since it was
    // read, and otherwise makes a concurrent cancel or status change wait until the payment
    // transaction ends, so an order cannot be cancelled while its payment is being charged.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForPayment(Order order) {
        entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(Order order, String reason) {
        applyStatus(order, OrderStatus.PAID, reason);
    }

    private OrderDetailResponse changeStatus(Order order, OrderStatus target, String reason) {
        applyStatus(order, target, reason);
        return detail(order);
    }

    // The versioned UPDATE is flushed before stock is touched: if another request changed the order
    // in the meantime this fails with a 409 straight away, and a cancellation can never release the
    // same stock twice. Stock goes back with the same atomic UPDATE style, in product id order.
    // A paid order is refunded after the order update and the stock release, so a database failure
    // happens before money moves, and a failed refund rolls the whole cancellation back.
    private void applyStatus(Order order, OrderStatus target, String reason) {
        OrderStatus previous = order.changeStatus(target);
        orderRepository.flush();
        if (target == OrderStatus.CANCELLED) {
            order.getItems().stream()
                    .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                    .forEach(item -> productService.releaseStock(item.getProduct().getId(), item.getQuantity()));
            boolean refunded = previous == OrderStatus.PAID && paymentRefunds.refundFor(order.getId());
            events.publishEvent(new OrderCancelledEvent(order.getId(), order.getOrderNumber(),
                    order.getUser().getEmail(), order.getTotalAmount(), refunded));
        }
        recordStatus(order, previous, reason);
    }

    private void recordStatus(Order order, OrderStatus from, String reason) {
        historyRepository.save(new OrderStatusHistory(order, from, order.getStatus(),
                auditor.getCurrentAuditor().orElseThrow(), clock.instant(), reason));
    }

    // The second query returns the orders in no particular order, so the page keeps the id order.
    private PageResponse<OrderResponse> withItems(Page<Long> ids) {
        Map<Long, Order> ordersById = ids.isEmpty() ? Map.of()
                : orderRepository.findWithItemsByIdIn(ids.getContent()).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity()));
        return PageResponse.from(ids.map(id -> OrderResponse.from(ordersById.get(id))));
    }

    private OrderDetailResponse detail(Order order) {
        return OrderDetailResponse.from(order, historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId()));
    }

    private Order ownedOrder(Long userId, Long orderId) {
        return orderRepository.findWithItemsByIdAndUserId(orderId, userId).orElseThrow(() -> notFound(orderId));
    }

    private Order anyOrder(Long orderId) {
        return orderRepository.findWithItemsById(orderId).orElseThrow(() -> notFound(orderId));
    }

    private static ResourceNotFoundException notFound(Long orderId) {
        return new ResourceNotFoundException("Order not found with id " + orderId);
    }
}
