package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.money.Money;
import com.darshan.ecommerce.common.persistence.BaseEntity;
import com.darshan.ecommerce.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String orderNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Always the sum of the rounded line totals, maintained by addItem and removeItem.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = Money.ZERO;

    @Column(nullable = false, length = 255)
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Version
    @Column(nullable = false)
    private Integer version;

    public Order(String orderNumber, User user, String shippingAddress) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.CREATED;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    public void removeItem(OrderItem item) {
        if (items.remove(item)) {
            item.setOrder(null);
            totalAmount = totalAmount.subtract(item.getLineTotal());
        }
    }

    // Returns the previous status so the caller can record the change. The @Version column makes
    // two concurrent changes of the same order fail instead of both being applied.
    OrderStatus changeStatus(OrderStatus target) {
        OrderStatus previous = status;
        status = status.moveTo(target);
        return previous;
    }
}
