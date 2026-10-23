package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentProcessorTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "4111111111111111", "tok_", "tok_has space", "pm_123"})
    void aCardPaymentNeedsAProviderToken(String token) {
        CardPaymentProcessor card = new CardPaymentProcessor(gateway);

        assertThatThrownBy(() -> card.validate(new PaymentRequest(PaymentMethod.CARD, token, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_TOKEN_INVALID"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "darshan", "@bank", "darshan@", "darshan@bank1", "a@b"})
    void aUpiPaymentNeedsAUpiAddress(String upiId) {
        UpiPaymentProcessor upi = new UpiPaymentProcessor(gateway);

        assertThatThrownBy(() -> upi.validate(new PaymentRequest(PaymentMethod.UPI, null, upiId)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("UPI_ID_INVALID"));
    }

    @Test
    void wellFormedInstrumentsAreAccepted() {
        assertThatCode(() -> new CardPaymentProcessor(gateway)
                .validate(new PaymentRequest(PaymentMethod.CARD, "tok_visa_4242", null))).doesNotThrowAnyException();
        assertThatCode(() -> new UpiPaymentProcessor(gateway)
                .validate(new PaymentRequest(PaymentMethod.UPI, null, "darshan.s@okbank"))).doesNotThrowAnyException();
        assertThatCode(() -> new CashOnDeliveryProcessor()
                .validate(new PaymentRequest(PaymentMethod.COD, null, null))).doesNotThrowAnyException();
    }

    @Test
    void cashOnDeliveryNeverCallsTheProvider() {
        CashOnDeliveryProcessor cod = new CashOnDeliveryProcessor();

        assertThat(cod.charge(null, new PaymentRequest(PaymentMethod.COD, null, null)))
                .isEqualTo(new GatewayResult.Approved(null));
        assertThat(cod.refund(null)).isEqualTo(new GatewayResult.Approved(null));
        verifyNoInteractions(gateway);
    }
}
