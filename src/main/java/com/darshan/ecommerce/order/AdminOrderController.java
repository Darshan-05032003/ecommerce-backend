package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.web.PageParams;
import com.darshan.ecommerce.common.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: orders", description = "Order administration")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    @Operation(summary = "List all orders, optionally only those in one status")
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @ParameterObject @Valid PageParams pageParams) {
        return orderService.listAll(status, pageParams.toPageable(OrderController.SORT));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get any order with its status history")
    public OrderDetailResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PatchMapping("/orders/{id}/status")
    @Operation(summary = "Move an order to another status; cancelling returns the stock")
    public OrderDetailResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.changeStatus(id, request);
    }

    @GetMapping("/users/{userId}/orders")
    @Operation(summary = "List the orders of any user")
    public PageResponse<OrderResponse> listForUser(@PathVariable Long userId,
                                                   @ParameterObject @Valid PageParams pageParams) {
        return orderService.listAnyUsersOrders(userId, pageParams.toPageable(OrderController.SORT));
    }
}
