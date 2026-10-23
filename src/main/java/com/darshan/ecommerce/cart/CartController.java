package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/cart")
@Tag(name = "Cart", description = "The authenticated user's shopping cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get my cart")
    public List<CartItemResponseDTO> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return cartService.getCartForUser(principal.getId());
    }

    @PostMapping
    @Operation(summary = "Add a product to my cart")
    public ResponseEntity<CartItemResponseDTO> addToCart(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestParam Long productId,
                                                         @RequestParam int quantity) {
        CartItemResponseDTO item = cartService.addToCart(principal.getId(), productId, quantity);
        return ResponseEntity.created(URI.create("/api/v1/me/cart")).body(item);
    }

    @DeleteMapping
    @Operation(summary = "Clear my cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clearCart(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
