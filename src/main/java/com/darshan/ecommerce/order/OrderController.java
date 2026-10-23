package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/me/orders")
@Tag(name = "Orders", description = "The authenticated user's orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List my orders")
    public Page<OrderResponseDTO> listOrders(@AuthenticationPrincipal UserPrincipal principal, Pageable pageable) {
        return orderService.listOrdersForUser(principal.getId(), pageable);
    }

    @PostMapping
    @Operation(summary = "Place an order from my cart")
    public ResponseEntity<OrderResponseDTO> placeOrder(@AuthenticationPrincipal UserPrincipal principal) {
        OrderResponseDTO order = orderService.placeOrder(principal.getId());
        return ResponseEntity.created(URI.create("/api/v1/me/orders/" + order.getId())).body(order);
    }
}
