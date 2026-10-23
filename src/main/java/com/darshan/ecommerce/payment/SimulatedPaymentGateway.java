package com.darshan.ecommerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// A stand-in for a real provider that answers from the payment instrument, so every outcome can be
// produced on purpose, the way providers' sandbox test cards work. Nothing is random:
//   instrument containing "decline"     -> declined
//   instrument containing "timeout"     -> timed out
//   instrument containing "refundfail"  -> approved, but a later refund is rejected
//   anything else                       -> approved
@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "simulated")
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private static final String REFERENCE_PREFIX = "SIM-";
    private static final String NO_REFUND_PREFIX = "SIM-NR-";

    public SimulatedPaymentGateway() {
        log.warn("Payments use the simulated gateway; no real money is moved");
    }

    @Override
    public GatewayResult charge(Long paymentId, PaymentMethod method, String instrument, BigDecimal amount) {
        String value = instrument.toLowerCase();
        if (value.contains("decline")) {
            return new GatewayResult.Declined("Declined by the issuer");
        }
        if (value.contains("timeout")) {
            return new GatewayResult.TimedOut();
        }
        String prefix = value.contains("refundfail") ? NO_REFUND_PREFIX : REFERENCE_PREFIX;
        return new GatewayResult.Approved(prefix + method + "-" + paymentId);
    }

    @Override
    public GatewayResult refund(String reference, BigDecimal amount) {
        if (reference.startsWith(NO_REFUND_PREFIX)) {
            return new GatewayResult.Declined("Refund rejected by the provider");
        }
        return new GatewayResult.Approved("RF-" + reference);
    }
}
