package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.persistence.BaseEntity;
import com.darshan.ecommerce.order.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// One row per payment attempt. Failed attempts are kept: they are the record of what the
// customer tried and why the provider said no.
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 100)
    private String gatewayReference;

    @Column(length = 255)
    private String failureReason;

    private Instant refundedAt;

    Payment(Order order, PaymentMethod method, BigDecimal amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.INITIATED;
    }

    void succeed(String gatewayReference) {
        requireInitiated();
        this.status = PaymentStatus.SUCCEEDED;
        this.gatewayReference = gatewayReference;
    }

    void fail(String reason) {
        requireInitiated();
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    void markRefunded(Instant when) {
        if (status != PaymentStatus.SUCCEEDED || refundedAt != null) {
            throw new IllegalStateException("Payment " + getId() + " cannot be refunded in status " + status);
        }
        this.refundedAt = when;
    }

    private void requireInitiated() {
        if (status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment " + getId() + " is already " + status);
        }
    }
}
