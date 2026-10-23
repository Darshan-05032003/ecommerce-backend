package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.InvalidRequestException;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
class UpiPaymentProcessor implements PaymentProcessor {

    private static final Pattern UPI_ID = Pattern.compile("[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}");

    private final PaymentGateway gateway;

    UpiPaymentProcessor(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public void validate(PaymentRequest request) {
        if (request.upiId() == null || !UPI_ID.matcher(request.upiId()).matches()) {
            throw new InvalidRequestException("UPI_ID_INVALID", "A UPI payment needs a upiId such as name@bank");
        }
    }

    @Override
    public GatewayResult charge(Payment payment, PaymentRequest request) {
        return gateway.charge(payment.getId(), method(), request.upiId(), payment.getAmount());
    }

    @Override
    public GatewayResult refund(Payment payment) {
        return gateway.refund(payment.getGatewayReference(), payment.getAmount());
    }
}
