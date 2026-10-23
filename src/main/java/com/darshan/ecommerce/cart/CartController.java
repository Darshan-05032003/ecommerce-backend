package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;
import com.darshan.ecommerce.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lines are addressed by product id: a cart holds at most one line per product, and the client
// already knows the product id, so no cart item id needs to leave the server.
@RestController
@RequestMapping("/api/v1/me/cart")
@Tag(name = ApiTags.CART)
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get my cart with line totals and the cart total")
    public CartResponse getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return cartService.getCart(principal.getId());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to my cart, or increase its quantity if it is already there")
    @ApiResponse(responseCode = "200", description = "The whole cart after the change")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "422", ref = ApiErrorResponses.UNPROCESSABLE)
    public CartResponse addItem(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(principal.getId(), request);
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Set the quantity of a product in my cart")
    @ApiResponse(responseCode = "200", description = "The whole cart after the change")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "422", ref = ApiErrorResponses.UNPROCESSABLE)
    public CartResponse updateQuantity(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long productId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(principal.getId(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from my cart")
    @ApiResponse(responseCode = "204", description = "Removed, or the product was not in the cart")
    public ResponseEntity<Void> removeLine(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long productId) {
        cartService.removeLine(principal.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Remove every product from my cart")
    @ApiResponse(responseCode = "204", description = "The cart is empty")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clear(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
