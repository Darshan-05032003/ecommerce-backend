package com.darshan.ecommerce.order;

import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.product.ProductService;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public Page<OrderResponseDTO> listOrdersForUser(Long userId, Pageable pageable) {
        User user = getUser(userId);
        return orderRepository.findByUser(user, pageable).map(this::mapToDTO);
    }

    public OrderResponseDTO placeOrder(Long userId) {
        User user = getUser(userId);
        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        if (cartItems.isEmpty()) {
            throw new BusinessRuleViolationException("Cart is empty");
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
        return mapToDTO(saved);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
    }
    
    private OrderResponseDTO mapToDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setUserId(order.getUser().getId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setStatus(order.getStatus().name());
        dto.setCreatedAt(order.getCreatedAt());
        
        if (order.getItems() != null) {
            dto.setItems(order.getItems().stream().map(item -> {
                OrderItemResponseDTO itemDTO = new OrderItemResponseDTO();
                itemDTO.setId(item.getId());
                itemDTO.setProductId(item.getProduct().getId());
                itemDTO.setProductName(item.getProduct().getName());
                itemDTO.setQuantity(item.getQuantity());
                itemDTO.setPrice(item.getPrice());
                return itemDTO;
            }).collect(Collectors.toList()));
        }
        
        return dto;
    }
}

