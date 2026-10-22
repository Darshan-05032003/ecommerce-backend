package com.darshan.ecommerce.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users/{userId}/orders")
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get all orders for a user with pagination")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getById(#userId).email")
    public ResponseEntity<Page<OrderResponseDTO>> listOrders(
            @PathVariable Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrdersForUser(userId, pageable));
    }

    @PostMapping
    @Operation(summary = "Place an order for a user")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getById(#userId).email")
    public ResponseEntity<OrderResponseDTO> placeOrder(@PathVariable Long userId) {
        OrderResponseDTO order = orderService.placeOrder(userId);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId + "/orders/" + order.getId()))
                .body(order);
    }
}

