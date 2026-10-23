package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.order.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Writes payment attempts in their own transactions (REQUIRES_NEW): the caller's transaction is
// suspended, these commit on a second connection, and the rows survive when the caller's
// transaction rolls back after a decline or a timeout. This has to be a separate bean: a
// REQUIRES_NEW method called on "this" inside PaymentService would bypass the Spring proxy and
// silently run in the caller's transaction.
@Service
public class PaymentAttemptLog {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentAttemptLog(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    // Flushed here so that a second live attempt for the same order fails on the unique index
    // inside this call, not later at some unrelated flush.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment start(Long orderId, PaymentMethod method, BigDecimal amount) {
        return paymentRepository.saveAndFlush(new Payment(orderRepository.getReferenceById(orderId), method, amount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long paymentId, String reason) {
        paymentRepository.findById(paymentId).orElseThrow().fail(reason);
    }
}
