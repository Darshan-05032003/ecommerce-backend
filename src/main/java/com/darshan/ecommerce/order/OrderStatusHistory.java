package com.darshan.ecommerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// An append-only audit row. It does not extend BaseEntity because it is never updated, and
// changedBy / changedAt already say who wrote it and when. Rows are never compared or put in
// sets, so the default identity equals is enough.
@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private OrderStatus toStatus;

    @Column(nullable = false, length = 120, updatable = false)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    @Column(length = 255, updatable = false)
    private String reason;

    public OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, String changedBy,
                              Instant changedAt, String reason) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.reason = reason;
    }
}
