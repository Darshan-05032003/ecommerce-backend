package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.order.Order;
import com.darshan.ecommerce.order.OrderService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 50L;
    private static final Long PAYMENT_ID = 900L;
    private static final PaymentRequest CARD = new PaymentRequest(PaymentMethod.CARD, "tok_visa", null);

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentProcessor cardProcessor;

    @Mock
    private PaymentAttemptLog attemptLog;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher events;

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private PaymentService paymentService;
    private Order order;
    private Payment attempt;

    @BeforeEach
    void setUp() {
        when(cardProcessor.method()).thenReturn(PaymentMethod.CARD);
        paymentService = new PaymentService(orderService, new PaymentProcessorRegistry(List.of(cardProcessor)),
                attemptLog, paymentRepository, events, new PaymentMetrics(registry));
        order = anOrder().id(ORDER_ID).user(aUser().email("alice@example.com").build())
                .line(aProduct().price("60.00").build(), 2).build();
        attempt = new Payment(order, PaymentMethod.CARD, order.getTotalAmount());
        ReflectionTestUtils.setField(attempt, "id", PAYMENT_ID);
        when(orderService.payableOrder(USER_ID, ORDER_ID)).thenReturn(order);
    }

    // The attempt is recorded (in its own transaction) before the order row is locked, and the
    // gateway is called only once the lock is held.
    @Test
    void anApprovedChargeMarksThePaymentAndTheOrderPaid() {
        when(attemptLog.start(ORDER_ID, PaymentMethod.CARD, new BigDecimal("120.00"))).thenReturn(attempt);
        when(cardProcessor.charge(attempt, CARD)).thenReturn(new GatewayResult.Approved("SIM-CARD-900"));
        when(paymentRepository.findForUpdateById(PAYMENT_ID)).thenReturn(Optional.of(attempt));

        PaymentResponse response = paymentService.pay(USER_ID, ORDER_ID, CARD);

        InOrder steps = inOrder(cardProcessor, attemptLog, orderService);
        steps.verify(cardProcessor).validate(CARD);
        steps.verify(attemptLog).start(ORDER_ID, PaymentMethod.CARD, new BigDecimal("120.00"));
        steps.verify(orderService).lockForPayment(order);
        steps.verify(cardProcessor).charge(attempt, CARD);
        steps.verify(orderService).markPaid(order, "Paid by CARD");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.gatewayReference()).isEqualTo("SIM-CARD-900");
        verify(events).publishEvent(new OrderPaidEvent(ORDER_ID, order.getOrderNumber(), "alice@example.com",
                new BigDecimal("120.00"), PaymentMethod.CARD));
    }

    @Test
    void anUnexpectedErrorWhileChargingIsRecordedAsANotProcessedAttempt() {
        when(attemptLog.start(any(), any(), any())).thenReturn(attempt);
        IllegalStateException failure = new IllegalStateException("Connection reset");
        when(cardProcessor.charge(attempt, CARD)).thenThrow(failure);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, ORDER_ID, CARD)).isSameAs(failure);

        verify(attemptLog).fail(PAYMENT_ID, "Not processed: IllegalStateException");
        verify(orderService, never()).markPaid(any(), any());
        verifyNoInteractions(events);
        assertThat(failures("CARD", "error")).isEqualTo(1);
        assertThat(failures("CARD", "declined")).isZero();
    }

    @Test
    void aSecondAttemptWhileOneIsInFlightIsAConflictAndNeverReachesTheGateway() {
        when(attemptLog.start(any(), any(), any())).thenThrow(new DataIntegrityViolationException("uk_payments_active_order"));

        assertThatThrownBy(() -> paymentService.pay(USER_ID, ORDER_ID, CARD))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A payment for this order is already in progress");

        verify(orderService, never()).lockForPayment(any());
        verify(cardProcessor, never()).charge(any(), any());
    }

    private double failures(String method, String reason) {
        return registry.get("payments.failed").tag("method", method).tag("reason", reason).counter().count();
    }
}
