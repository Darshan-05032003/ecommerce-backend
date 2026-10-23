package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;
import com.darshan.ecommerce.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = ApiTags.PAYMENTS)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay for one of my orders with CARD, UPI or COD; the order must be CREATED")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "card", summary = "Card token from the provider's client SDK",
                    description = "The API never receives card numbers",
                    value = """
                            {"method": "CARD", "paymentToken": "tok_visa"}"""),
            @ExampleObject(name = "upi", summary = "UPI address of the customer",
                    description = "Collected through the customer's UPI app",
                    value = """
                            {"method": "UPI", "upiId": "jane@okbank"}"""),
            @ExampleObject(name = "cod", summary = "Cash on delivery",
                    description = "Confirms the order without calling the payment gateway",
                    value = """
                            {"method": "COD"}""")}))
    @ApiResponse(responseCode = "201", description = "Payment succeeded; the order is now PAID")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    @ApiResponse(responseCode = "422", ref = ApiErrorResponses.UNPROCESSABLE)
    @ApiResponse(responseCode = "504", ref = ApiErrorResponses.GATEWAY_TIMEOUT)
    public PaymentResponse pay(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long orderId,
                               @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(principal.getId(), orderId, request);
    }
}
