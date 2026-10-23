package com.darshan.ecommerce.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private static final BigDecimal AMOUNT = new BigDecimal("49.99");

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

    @Test
    void theInstrumentDecidesTheOutcome() {
        assertThat(gateway.charge(7L, PaymentMethod.CARD, "tok_visa", AMOUNT))
                .isEqualTo(new GatewayResult.Approved("SIM-CARD-7"));
        assertThat(gateway.charge(8L, PaymentMethod.CARD, "tok_decline", AMOUNT))
                .isEqualTo(new GatewayResult.Declined("Declined by the issuer"));
        assertThat(gateway.charge(9L, PaymentMethod.UPI, "timeout@upi", AMOUNT))
                .isEqualTo(new GatewayResult.TimedOut());
    }

    @Test
    void refundsSucceedUnlessTheChargeWasMarkedToRejectThem() {
        GatewayResult.Approved normal = (GatewayResult.Approved) gateway.charge(1L, PaymentMethod.UPI, "darshan@okbank", AMOUNT);
        GatewayResult.Approved noRefund = (GatewayResult.Approved) gateway.charge(2L, PaymentMethod.CARD, "tok_refundfail", AMOUNT);

        assertThat(gateway.refund(normal.reference(), AMOUNT)).isEqualTo(new GatewayResult.Approved("RF-SIM-UPI-1"));
        assertThat(gateway.refund(noRefund.reference(), AMOUNT))
                .isEqualTo(new GatewayResult.Declined("Refund rejected by the provider"));
    }
}
