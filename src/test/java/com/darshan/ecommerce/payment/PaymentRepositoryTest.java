package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.persistence.PersistenceTest;
import com.darshan.ecommerce.order.Order;
import com.darshan.ecommerce.order.OrderRepository;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PersistenceTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private Order order;

    @BeforeEach
    void createOrder() {
        Category category = categoryRepository.save(new Category("Payments", null));
        order = orderRepository.saveAndFlush(anOrder()
                .user(userRepository.save(aUser().build()))
                .line(productRepository.save(aProduct().price("250.00").category(category).build()), 2)
                .build());
    }

    @Test
    void findsOnlyTheSucceededPaymentThatHasNotBeenRefunded() {
        failedAttempt(PaymentMethod.CARD);
        Payment paid = attempt(PaymentMethod.UPI);
        paid.succeed("SIM-UPI-1");
        paymentRepository.flush();

        assertThat(paymentRepository.findRefundable(order.getId())).containsSame(paid);

        paid.markRefunded(Instant.parse("2026-03-16T09:00:00Z"));
        paymentRepository.flush();

        assertThat(paymentRepository.findRefundable(order.getId())).isEmpty();
    }

    // The generated active_order_id column is what stops two live payments for one order, even if
    // two requests pass every check in the service at the same moment.
    @Test
    void allowsManyFailedAttemptsButOnlyOneLivePaymentPerOrder() {
        failedAttempt(PaymentMethod.CARD);
        failedAttempt(PaymentMethod.CARD);
        attempt(PaymentMethod.UPI);

        assertThatThrownBy(() -> attempt(PaymentMethod.COD))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payments_active_order");
    }

    private Payment attempt(PaymentMethod method) {
        return paymentRepository.saveAndFlush(new Payment(order, method, order.getTotalAmount()));
    }

    // Flushed straight away: Hibernate runs pending INSERTs before UPDATEs, so the next attempt's
    // INSERT would otherwise reach the unique index while this row still looks live.
    private void failedAttempt(PaymentMethod method) {
        attempt(method).fail("Declined by the issuer");
        paymentRepository.flush();
    }
}
