package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.UpstreamServiceException;
import com.darshan.ecommerce.order.Order;
import com.darshan.ecommerce.order.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final String PAYMENT_DECLINED = "PAYMENT_DECLINED";
    static final String PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";

    private final OrderService orderService;
    private final PaymentProcessorRegistry processors;
    private final PaymentAttemptLog attemptLog;
    private final PaymentRepository paymentRepository;

    public PaymentService(OrderService orderService, PaymentProcessorRegistry processors,
                          PaymentAttemptLog attemptLog, PaymentRepository paymentRepository) {
        this.orderService = orderService;
        this.processors = processors;
        this.attemptLog = attemptLog;
        this.paymentRepository = paymentRepository;
    }

    // The attempt row is written by PaymentAttemptLog in its own transactions, so it is kept
    // whatever happens here. A success marks the payment SUCCEEDED and the order PAID in this
    // transaction, so the two always commit together. A decline or timeout records the failure,
    // then throws, and this transaction rolls back: the order stays CREATED.
    @Transactional
    public PaymentResponse pay(Long userId, Long orderId, PaymentRequest request) {
        PaymentProcessor processor = processors.processorFor(request.method());
        processor.validate(request);
        Order order = orderService.payableOrder(userId, orderId);

        Payment attempt = start(order, request.method());
        GatewayResult result;
        try {
            // The order row is locked only now: the attempt INSERT above checks its foreign key with
            // a shared lock on the same order row, from another connection, and would wait forever
            // behind a lock this transaction already held.
            orderService.lockForPayment(order);
            result = processor.charge(attempt, request);
        } catch (RuntimeException ex) {
            attemptLog.fail(attempt.getId(), "Not processed: " + ex.getClass().getSimpleName());
            throw ex;
        }

        return switch (result) {
            case GatewayResult.Approved(String reference) -> succeed(order, attempt.getId(), reference);
            case GatewayResult.Declined(String reason) -> {
                attemptLog.fail(attempt.getId(), reason);
                log.info("Payment {} for order {} declined", attempt.getId(), orderId);
                throw new BusinessRuleViolationException(PAYMENT_DECLINED, "The payment was declined: " + reason);
            }
            case GatewayResult.TimedOut() -> {
                attemptLog.fail(attempt.getId(), "Payment provider timed out");
                log.warn("Payment {} for order {} timed out at the provider", attempt.getId(), orderId);
                throw UpstreamServiceException.gatewayTimeout(PAYMENT_TIMEOUT,
                        "The payment provider did not answer in time; the order was not paid");
            }
        };
    }

    private Payment start(Order order, PaymentMethod method) {
        try {
            return attemptLog.start(order.getId(), method, order.getTotalAmount());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A payment for this order is already in progress");
        }
    }

    // Read with a lock rather than a plain find: the row was committed by another transaction after
    // this one took its REPEATABLE READ snapshot, so a plain read would not see it.
    private PaymentResponse succeed(Order order, Long paymentId, String reference) {
        Payment payment = paymentRepository.findForUpdateById(paymentId).orElseThrow();
        payment.succeed(reference);
        orderService.markPaid(order, "Paid by " + payment.getMethod());
        log.info("Payment {} for order {} succeeded", paymentId, order.getId());
        return PaymentResponse.from(payment, order.getId());
    }
}
