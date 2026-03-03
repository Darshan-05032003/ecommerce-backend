package com.example.ecommerce.service;

import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository,
                        CartItemRepository cartItemRepository,
                        UserRepository userRepository,
                        ProductService productService) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
    }

    public List<Order> listOrdersForUser(Long userId) {
        User user = getUser(userId);
        return orderRepository.findByUser(user);
    }

    public Order placeOrder(Long userId) {
        User user = getUser(userId);
        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            productService.decreaseStock(cartItem.getProduct().getId(), cartItem.getQuantity());

            BigDecimal lineTotal = cartItem.getProduct()
                    .getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .product(cartItem.getProduct())
                    .quantity(cartItem.getQuantity())
                    .price(cartItem.getProduct().getPrice())
                    .build();
            orderItems.add(orderItem);
        }

        Order order = Order.builder()
                .user(user)
                .totalAmount(total)
                .status(Order.OrderStatus.CREATED)
                .build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteByUser(user);
        return saved;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id " + userId));
    }
}

