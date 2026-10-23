package com.darshan.ecommerce.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: orders", description = "Order administration")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List the orders of any user")
    public Page<OrderResponse> listOrders(@PathVariable Long userId, Pageable pageable) {
        return orderService.listOrdersForUser(userId, pageable);
    }
}
