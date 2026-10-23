package com.darshan.ecommerce.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-03-16T09:00:00Z");

    @Test
    void anAttemptEndsOnlyOnce() {
        Payment declined = attempt();
        declined.fail("Declined by the issuer");

        assertThatThrownBy(() -> declined.succeed("SIM-CARD-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("is already FAILED");
        assertThat(declined.getGatewayReference()).isNull();

        Payment paid = attempt();
        paid.succeed("SIM-CARD-2");

        assertThatThrownBy(() -> paid.fail("Late failure")).isInstanceOf(IllegalStateException.class);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void onlyASucceededPaymentCanBeRefundedAndOnlyOnce() {
        Payment declined = attempt();
        declined.fail("Declined by the issuer");
        assertThatThrownBy(() -> declined.markRefunded(NOW)).isInstanceOf(IllegalStateException.class);

        Payment paid = attempt();
        paid.succeed("SIM-CARD-3");
        paid.markRefunded(NOW);

        assertThatThrownBy(() -> paid.markRefunded(NOW.plusSeconds(60))).isInstanceOf(IllegalStateException.class);
        assertThat(paid.getRefundedAt()).isEqualTo(NOW);
    }

    private static Payment attempt() {
        return new Payment(anOrder().build(), PaymentMethod.CARD, new BigDecimal("10.00"));
    }
}
