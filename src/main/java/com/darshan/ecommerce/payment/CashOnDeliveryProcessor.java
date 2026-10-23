package com.darshan.ecommerce.payment;

import org.springframework.stereotype.Component;

// Cash on delivery confirms the order without moving money: the courier collects the cash at the
// door, which happens after shipping. So there is no provider call, no reference, and nothing to
// refund when the order is cancelled (an order can only be cancelled before it ships).
@Component
class CashOnDeliveryProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COD;
    }

    @Override
    public GatewayResult charge(Payment payment, PaymentRequest request) {
        return new GatewayResult.Approved(null);
    }

    @Override
    public GatewayResult refund(Payment payment) {
        return new GatewayResult.Approved(null);
    }
}
