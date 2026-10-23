package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;
import com.darshan.ecommerce.common.security.UserPrincipal;
import com.darshan.ecommerce.common.web.PageParams;
import com.darshan.ecommerce.common.web.PageResponse;
import com.darshan.ecommerce.common.web.SortWhitelist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = ApiTags.ORDERS)
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
    @ApiResponse(responseCode = "200", description = "The order with its lines and status history")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    public OrderDetailResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return orderService.getForUser(principal.getId(), id);
    }

    // The header is optional at the binding level so that a missing key gets the same problem body
    // with an error code as a malformed one, instead of Spring's generic missing-header response.
    @PostMapping
    @Operation(summary = "Place an order from my cart; requires an Idempotency-Key header")
    @ApiResponse(responseCode = "201", description = "Order created, or the stored response of an earlier request "
            + "with the same key", headers = @Header(name = IDEMPOTENT_REPLAYED,
            description = "true when the response is a replay and nothing new was placed",
            schema = @Schema(type = "boolean")))
    @ApiResponse(responseCode = "422", ref = ApiErrorResponses.UNPROCESSABLE)
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Parameter(required = true, example = "8f14e45f-ceea-467f-a8f4-2b2b1a7c4c55",
                                                            description = "Client-generated key, 1 to 100 letters, digits or . _ : -; "
                                                                    + "a retry with the same key and body returns the original order")
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
    @ApiResponse(responseCode = "200", description = "The cancelled order; a paid order was refunded")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public OrderDetailResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return orderService.cancelForUser(principal.getId(), id);
    }

    private static URI location(OrderResponse order) {
        return URI.create("/api/v1/me/orders/" + order.id());
    }
}
