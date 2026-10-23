package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.security.UserPrincipal;
import com.darshan.ecommerce.common.web.PageParams;
import com.darshan.ecommerce.common.web.PageResponse;
import com.darshan.ecommerce.common.web.SortWhitelist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/me/orders")
@Tag(name = "Orders", description = "The authenticated user's orders")
public class OrderController {

    static final SortWhitelist SORT = SortWhitelist.of(Sort.Direction.DESC, "createdAt", "totalAmount");
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderService orderService;
    private final OrderPlacementService placementService;

    public OrderController(OrderService orderService, OrderPlacementService placementService) {
        this.orderService = orderService;
        this.placementService = placementService;
    }

    @GetMapping
    @Operation(summary = "List my orders, newest first by default")
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                            @ParameterObject @Valid PageParams pageParams) {
        return orderService.listForUser(principal.getId(), pageParams.toPageable(SORT));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my orders with its status history")
    public OrderDetailResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return orderService.getForUser(principal.getId(), id);
    }

    // The header is optional at the binding level so that a missing key gets the same problem body
    // with an error code as a malformed one, instead of Spring's generic missing-header response.
    @PostMapping
    @Operation(summary = "Place an order from my cart; requires an Idempotency-Key header")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                                    @Valid @RequestBody PlaceOrderRequest request) {
        return switch (placementService.place(principal.getId(), key, request)) {
            case PlacementResult.Created(OrderResponse order) ->
                    ResponseEntity.created(location(order)).body(order);
            case PlacementResult.Replayed(int status, OrderResponse order) ->
                    ResponseEntity.status(status).location(location(order))
                            .header(IDEMPOTENT_REPLAYED, "true").body(order);
        };
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel one of my orders that has not shipped yet; the stock is returned")
    public OrderDetailResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return orderService.cancelForUser(principal.getId(), id);
    }

    private static URI location(OrderResponse order) {
        return URI.create("/api/v1/me/orders/" + order.id());
    }
}
