package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.common.exception.UpstreamServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundsTest {

    private static final Long ORDER_ID = 50L;
    private static final Instant NOW = Instant.parse("2026-03-16T09:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProcessor upiProcessor;

    private PaymentRefunds refunds;
    private Payment payment;

    @BeforeEach
    void setUp() {
        when(upiProcessor.method()).thenReturn(PaymentMethod.UPI);
        refunds = new PaymentRefunds(paymentRepository, new PaymentProcessorRegistry(List.of(upiProcessor)),
                new MutableClock(NOW));
        payment = new Payment(anOrder().line(aProduct().price("30.00").build(), 1).build(), PaymentMethod.UPI,
                null);
        payment.succeed("SIM-UPI-1");
    }

    @Test
    void anApprovedRefundStampsThePaymentWithTheClock() {
        when(paymentRepository.findRefundable(ORDER_ID)).thenReturn(Optional.of(payment));
        when(upiProcessor.refund(payment)).thenReturn(new GatewayResult.Approved("RF-SIM-UPI-1"));

        assertThat(refunds.refundFor(ORDER_ID)).isTrue();
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    // A timeout means the provider may or may not have refunded. The cancellation is rolled back
    // so the order stays PAID and can be retried or reconciled, rather than claiming a refund.
    @Test
    void aRefundTimeoutFailsTheCancellationWith504() {
        when(paymentRepository.findRefundable(ORDER_ID)).thenReturn(Optional.of(payment));
        when(upiProcessor.refund(payment)).thenReturn(new GatewayResult.TimedOut());

        assertThatThrownBy(() -> refunds.refundFor(ORDER_ID))
                .isInstanceOfSatisfying(UpstreamServiceException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_TIMEOUT");
                });
        assertThat(payment.getRefundedAt()).isNull();
    }

    @Test
    void anOrderWithoutARecordedPaymentHasNothingToRefund() {
        when(paymentRepository.findRefundable(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(refunds.refundFor(ORDER_ID)).isFalse();
        verify(upiProcessor, never()).refund(any());
    }
}
