package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentProcessorRegistryTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final CardPaymentProcessor card = new CardPaymentProcessor(gateway);
    private final UpiPaymentProcessor upi = new UpiPaymentProcessor(gateway);
    private final CashOnDeliveryProcessor cod = new CashOnDeliveryProcessor();

    @Test
    void picksTheProcessorRegisteredForEachMethod() {
        PaymentProcessorRegistry registry = new PaymentProcessorRegistry(List.of(cod, card, upi));

        assertThat(registry.processorFor(PaymentMethod.CARD)).isSameAs(card);
        assertThat(registry.processorFor(PaymentMethod.UPI)).isSameAs(upi);
        assertThat(registry.processorFor(PaymentMethod.COD)).isSameAs(cod);
    }

    @Test
    void aMethodWithoutAProcessorIsRejected() {
        PaymentProcessorRegistry registry = new PaymentProcessorRegistry(List.of(card));

        assertThatThrownBy(() -> registry.processorFor(PaymentMethod.UPI))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(PaymentProcessorRegistry.METHOD_UNSUPPORTED))
                .hasMessage("Payment method UPI is not supported");
    }

    @Test
    void twoProcessorsForTheSameMethodFailAtConstruction() {
        CardPaymentProcessor secondCard = new CardPaymentProcessor(gateway);

        assertThatThrownBy(() -> new PaymentProcessorRegistry(List.of(card, upi, secondCard)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Two payment processors for CARD: CardPaymentProcessor and CardPaymentProcessor");
    }

    @Test
    void anApplicationWithTwoProcessorsForOneMethodDoesNotStart() {
        new ApplicationContextRunner()
                .withBean(PaymentGateway.class, () -> gateway)
                .withBean("cardProcessor", CardPaymentProcessor.class, () -> new CardPaymentProcessor(gateway))
                .withBean("legacyCardProcessor", CardPaymentProcessor.class, () -> new CardPaymentProcessor(gateway))
                .withBean(PaymentProcessorRegistry.class)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("Two payment processors for CARD"));
    }
}
