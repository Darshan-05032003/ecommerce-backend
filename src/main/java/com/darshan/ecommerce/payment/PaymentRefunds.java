package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.UpstreamServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// Refunds run inside the cancellation transaction (MANDATORY), as its last step: the order update
// and the stock release have already been flushed, so database failures happen before money moves.
// If the provider refuses or does not answer, the exception rolls the whole cancellation back: the
// order stays PAID and the customer (or support) can try again. Nothing is half done.
@Service
public class PaymentRefunds {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefunds.class);

    static final String REFUND_FAILED = "PAYMENT_REFUND_FAILED";
    static final String PAYMENT_TIMEOUT = PaymentService.PAYMENT_TIMEOUT;

    private final PaymentRepository paymentRepository;
    private final PaymentProcessorRegistry processors;
    private final Clock clock;

    public PaymentRefunds(PaymentRepository paymentRepository, PaymentProcessorRegistry processors, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.processors = processors;
        this.clock = clock;
    }

    // Returns whether a payment was refunded. An order marked PAID by an admin without a recorded
    // payment (for example a bank transfer checked by hand) has nothing to refund here.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean refundFor(Long orderId) {
        return paymentRepository.findRefundable(orderId)
                .map(this::refund)
                .orElse(false);
    }

    private boolean refund(Payment payment) {
        return switch (processors.processorFor(payment.getMethod()).refund(payment)) {
            case GatewayResult.Approved(String reference) -> {
                payment.markRefunded(clock.instant());
                log.info("Payment {} refunded", payment.getId());
                yield true;
            }
            case GatewayResult.Declined(String reason) -> {
                log.warn("Refund of payment {} rejected: {}", payment.getId(), reason);
                throw UpstreamServiceException.badGateway(REFUND_FAILED,
                        "The payment provider rejected the refund; the order was not cancelled");
            }
            case GatewayResult.TimedOut() -> {
                log.warn("Refund of payment {} timed out at the provider", payment.getId());
                throw UpstreamServiceException.gatewayTimeout(PAYMENT_TIMEOUT,
                        "The payment provider did not answer in time; the order was not cancelled");
            }
        };
    }
}
