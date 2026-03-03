package com.example.ecommerce.controller;

import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartItem> getCart(@PathVariable Long userId) {
        return cartService.getCartForUser(userId);
    }

    @PostMapping
    public ResponseEntity<CartItem> addToCart(@PathVariable Long userId,
                                              @RequestParam Long productId,
                                              @RequestParam int quantity) {
        CartItem item = cartService.addToCart(userId, productId, quantity);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId + "/cart"))
                .body(item);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}

