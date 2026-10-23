package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.InvalidRequestException;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
class CardPaymentProcessor implements PaymentProcessor {

    private static final Pattern TOKEN = Pattern.compile("tok_[A-Za-z0-9_]{1,96}");

    private final PaymentGateway gateway;

    CardPaymentProcessor(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public void validate(PaymentRequest request) {
        if (request.paymentToken() == null || !TOKEN.matcher(request.paymentToken()).matches()) {
            throw new InvalidRequestException("PAYMENT_TOKEN_INVALID",
                    "A card payment needs a paymentToken of the form tok_...");
        }
    }

    @Override
    public GatewayResult charge(Payment payment, PaymentRequest request) {
        return gateway.charge(payment.getId(), method(), request.paymentToken(), payment.getAmount());
    }

    @Override
    public GatewayResult refund(Payment payment) {
        return gateway.refund(payment.getGatewayReference(), payment.getAmount());
    }
}
