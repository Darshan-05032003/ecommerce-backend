package com.darshan.ecommerce.notification;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.RecordingMailSender;
import com.darshan.ecommerce.RecordingMailSender.SentMail;
import com.darshan.ecommerce.order.OrderPlacedEvent;
import com.darshan.ecommerce.product.Product;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class OrderNotificationIntegrationTest {

    private static final String PASSWORD = "Str0ng!pw";
    private static final String ALICE = "alice@notify.example";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestApi api;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ThreadPoolTaskExecutor asyncExecutor;

    private String aliceToken;
    private Product book;

    @BeforeAll
    void createUser() throws Exception {
        api.cleanDatabase();
        api.registerCustomer(ALICE, PASSWORD);
        aliceToken = api.login(ALICE, PASSWORD).accessToken();
    }

    @BeforeEach
    void freshCatalogue() {
        api.cleanCatalogue();
        book = api.createProduct("BK-MAIL", new BigDecimal("12.00"), 10, api.createCategory("Books"));
    }

    @Test
    void placingAnOrderEmailsTheCustomerFromTheAsyncPoolWithTheRequestContext() throws Exception {
        String orderNumber = placeOrder("mail-req-1").get("orderNumber").asString();

        SentMail mail = awaitMail(ALICE, "Order " + orderNumber + " received");

        assertThat(mail.message().getFrom()).isEqualTo("orders@ecommerce.local");
        assertThat(mail.message().getText()).contains("Total: 24.00");
        assertThat(mail.thread()).startsWith("async-");
        assertThat(mail.correlationId()).isEqualTo("mail-req-1");
        assertThat(mail.user()).isEqualTo(ALICE);
    }

    @Test
    void paymentAndCancellationAreEmailedToo() throws Exception {
        JsonNode order = placeOrder("mail-req-2");
        String orderNumber = order.get("orderNumber").asString();
        long orderId = order.get("id").asLong();

        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/payments").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("method", "UPI", "upiId", "alice@okbank"))))
                .andExpect(status().isCreated());
        assertThat(awaitMail(ALICE, "Order " + orderNumber + " confirmed").message().getText())
                .isEqualTo("We received your UPI payment of 24.00 for order " + orderNumber + ".");

        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
        assertThat(awaitMail(ALICE, "Order " + orderNumber + " cancelled").message().getText())
                .isEqualTo("Your order " + orderNumber + " has been cancelled. A refund of 24.00 has been issued.");
    }

    // AFTER_COMMIT listeners are registered as transaction synchronizations. On rollback the
    // synchronization never fires, so no task even reaches the executor; on commit it does.
    @Test
    void anEventFromARolledBackTransactionSendsNothing() {
        String recipient = "rollback@notify.example";
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long tasksBefore = asyncExecutor.getThreadPoolExecutor().getTaskCount();

        transaction.executeWithoutResult(status -> {
            events.publishEvent(new OrderPlacedEvent(1L, "ORD-ROLLED-BACK", recipient, new BigDecimal("5.00")));
            status.setRollbackOnly();
        });

        assertThat(asyncExecutor.getThreadPoolExecutor().getTaskCount()).isEqualTo(tasksBefore);

        transaction.executeWithoutResult(status ->
                events.publishEvent(new OrderPlacedEvent(2L, "ORD-COMMITTED", recipient, new BigDecimal("5.00"))));

        awaitMail(recipient, "Order ORD-COMMITTED received");
        assertThat(mailSender.sentTo(recipient)).extracting(mail -> mail.message().getSubject())
                .containsExactly("Order ORD-COMMITTED received");
    }

    @Test
    void anEventPublishedOutsideATransactionIsIgnored() {
        String recipient = "no-tx@notify.example";
        long tasksBefore = asyncExecutor.getThreadPoolExecutor().getTaskCount();

        events.publishEvent(new OrderPlacedEvent(3L, "ORD-NO-TX", recipient, new BigDecimal("5.00")));

        assertThat(asyncExecutor.getThreadPoolExecutor().getTaskCount()).isEqualTo(tasksBefore);
        assertThat(mailSender.sentTo(recipient)).isEmpty();
    }

    @Test
    void aFailedSendIsReportedByTheAsyncExceptionHandler(CapturedOutput output) {
        String recipient = "bounce@notify.example";
        mailSender.failFor(recipient);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                events.publishEvent(new OrderPlacedEvent(4L, "ORD-BOUNCE", recipient, new BigDecimal("5.00"))));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(output.getOut())
                .contains("Async task OrderNotificationListener.onOrderPlaced failed: MailSendException")
                .doesNotContain(recipient));
        assertThat(mailSender.sentTo(recipient)).isEmpty();
    }

    private JsonNode placeOrder(String correlationId) throws Exception {
        mockMvc.perform(post("/api/v1/me/cart/items").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("productId", book.getId(), "quantity", 2))))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/v1/me/orders").header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("shippingAddress", "5 Mail Lane"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return api.read(body);
    }

    private SentMail awaitMail(String recipient, String subject) {
        return await().atMost(TIMEOUT).until(() -> find(mailSender.sentTo(recipient), subject), mail -> mail != null);
    }

    private static SentMail find(List<SentMail> mails, String subject) {
        return mails.stream().filter(mail -> mail.message().getSubject().equals(subject)).findFirst().orElse(null);
    }

    private String bearer() {
        return "Bearer " + aliceToken;
    }
}
