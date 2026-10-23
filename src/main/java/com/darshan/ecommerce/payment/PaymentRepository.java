package com.darshan.ecommerce.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // A locking read is a "current read" in InnoDB: it sees rows committed after this transaction's
    // snapshot was taken, such as the attempt row inserted by the REQUIRES_NEW attempt log.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForUpdateById(@Param("id") Long id);

    @Query("""
            select p from Payment p
            where p.order.id = :orderId and p.status = com.darshan.ecommerce.payment.PaymentStatus.SUCCEEDED
              and p.refundedAt is null""")
    Optional<Payment> findRefundable(@Param("orderId") Long orderId);
}
