package com.darshan.ecommerce.notification;

import com.darshan.ecommerce.common.logging.LogMasking;
import com.darshan.ecommerce.order.OrderCancelledEvent;
import com.darshan.ecommerce.order.OrderPlacedEvent;
import com.darshan.ecommerce.payment.OrderPaidEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// AFTER_COMMIT: the email is only sent once the order change is really in the database, never for
// a transaction that rolled back. @Async: the SMTP round trip runs on the async pool, so a slow
// mail server does not hold up the HTTP response. Delivery is at most once: if the application
// stops after the commit but before the send, or the send fails, the email is lost (see
// LoggingAsyncExceptionHandler); an outbox table would be needed for guaranteed delivery.
@Component
public class OrderNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public OrderNotificationListener(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        send(event.customerEmail(), "Order " + event.orderNumber() + " received",
                "Thank you for your order " + event.orderNumber() + ". Total: " + event.totalAmount() + ".");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        send(event.customerEmail(), "Order " + event.orderNumber() + " confirmed",
                "We received your " + event.method() + " payment of " + event.amount() + " for order "
                        + event.orderNumber() + ".");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        String refund = event.refunded() ? " A refund of " + event.totalAmount() + " has been issued." : "";
        send(event.customerEmail(), "Order " + event.orderNumber() + " cancelled",
                "Your order " + event.orderNumber() + " has been cancelled." + refund);
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Sent \"{}\" to {}", subject, LogMasking.email(to));
    }
}
