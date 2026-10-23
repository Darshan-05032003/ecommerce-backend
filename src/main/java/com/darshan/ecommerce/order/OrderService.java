package com.darshan.ecommerce.order;

import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class OrderService {

    static final String CART_EMPTY = "CART_EMPTY";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final AuditorAware<String> auditor;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository historyRepository,
                        CartItemRepository cartItemRepository,
                        UserRepository userRepository,
                        ProductService productService,
                        OrderNumberGenerator orderNumberGenerator,
                        AuditorAware<String> auditor,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.orderNumberGenerator = orderNumberGenerator;
        this.auditor = auditor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrdersForUser(Long userId, Pageable pageable) {
        User user = getUser(userId);
        return orderRepository.findByUser(user, pageable).map(OrderResponse::from);
    }

    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        // Products are joined into the cart query, and the lines are reserved in product id order so
        // two orders touching the same products lock the rows in the same order and cannot deadlock.
        List<CartItem> cartItems = cartItemRepository.findCartWithProducts(userId).stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        if (cartItems.isEmpty()) {
            throw new BusinessRuleViolationException(CART_EMPTY, "Cart is empty");
        }

        Order order = new Order(orderNumberGenerator.next(), userRepository.getReferenceById(userId),
                request.shippingAddress().strip());
        for (CartItem line : cartItems) {
            productService.reserveStock(line.getProduct().getId(), line.getQuantity());
            order.addItem(new OrderItem(line.getProduct(), line.getQuantity(), line.getProduct().getPrice()));
        }

        Order saved = orderRepository.save(order);
        recordStatus(saved, null, "Order placed");
        cartItemRepository.deleteAllByIdInBatch(cartItems.stream().map(CartItem::getId).toList());
        return OrderResponse.from(saved);
    }

    private void recordStatus(Order order, OrderStatus from, String reason) {
        historyRepository.save(new OrderStatusHistory(order, from, order.getStatus(),
                auditor.getCurrentAuditor().orElseThrow(), clock.instant(), reason));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
    }
}
