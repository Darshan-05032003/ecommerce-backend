package com.example.ecommerce.service;

import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    public CartService(CartItemRepository cartItemRepository,
                       UserRepository userRepository,
                       ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
    }

    public List<CartItem> getCartForUser(Long userId) {
        User user = getUser(userId);
        return cartItemRepository.findByUser(user);
    }

    public CartItem addToCart(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        User user = getUser(userId);
        Product product = productService.getById(productId);

        CartItem cartItem = cartItemRepository
                .findByUserIdAndProductId(userId, productId)
                .orElseGet(() -> CartItem.builder()
                        .user(user)
                        .product(product)
                        .quantity(0)
                        .build());

        cartItem.setQuantity(cartItem.getQuantity() + quantity);
        return cartItemRepository.save(cartItem);
    }

    public void clearCart(Long userId) {
        User user = getUser(userId);
        cartItemRepository.deleteByUser(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id " + userId));
    }
}

