package com.example.ecommerce.controller;

import com.example.ecommerce.dto.CartItemResponseDTO;
import com.example.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/cart")
@Tag(name = "Cart", description = "Shopping cart APIs")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get user's cart")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getEntityById(#userId).email")
    public List<CartItemResponseDTO> getCart(@PathVariable Long userId) {
        return cartService.getCartForUser(userId);
    }

    @PostMapping
    @Operation(summary = "Add product to cart")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getEntityById(#userId).email")
    public ResponseEntity<CartItemResponseDTO> addToCart(@PathVariable Long userId,
                                              @RequestParam Long productId,
                                              @RequestParam int quantity) {
        CartItemResponseDTO item = cartService.addToCart(userId, productId, quantity);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId + "/cart"))
                .body(item);
    }

    @DeleteMapping
    @Operation(summary = "Clear user's cart")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getEntityById(#userId).email")
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}

