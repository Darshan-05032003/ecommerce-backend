package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/orders/{orderId}/payments")
@Tag(name = "Payments", description = "Paying for the authenticated user's orders")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay for one of my orders with CARD, UPI or COD; the order must be CREATED")
    public PaymentResponse pay(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long orderId,
                               @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(principal.getId(), orderId, request);
    }
}
