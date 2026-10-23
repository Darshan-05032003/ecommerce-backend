package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.crypto.Sha256;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.InvalidRequestException;
import com.darshan.ecommerce.common.observability.LogExecutionTime;
import com.darshan.ecommerce.order.IdempotencyKeyStore.StoredResponse;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.regex.Pattern;

// Makes POST /me/orders safe to retry. The key row is inserted as the first statement of the
// order transaction, so the unique key (user_id, idem_key) decides which of two concurrent
// requests with the same key places the order: the second INSERT waits for the first
// transaction's row lock, and fails with a duplicate key once that transaction commits. It then
// starts over, finds the committed key and replays the stored response. If the first request
// rolls back (out of stock, empty cart), its key row disappears with it and the key stays usable.
@Service
@LogExecutionTime
public class OrderPlacementService {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

    static final int MAX_ATTEMPTS = 3;
    static final String KEY_MISSING = "IDEMPOTENCY_KEY_MISSING";
    static final String KEY_INVALID = "IDEMPOTENCY_KEY_INVALID";
    static final String KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final OrderService orderService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final IdempotencyKeyStore keyStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final OrderMetrics metrics;

    public OrderPlacementService(OrderService orderService, OrderNumberGenerator orderNumberGenerator,
                                 IdempotencyKeyStore keyStore, ObjectMapper objectMapper,
                                 PlatformTransactionManager transactionManager, OrderMetrics metrics) {
        this.orderService = orderService;
        this.orderNumberGenerator = orderNumberGenerator;
        this.keyStore = keyStore;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    // The timer stops after the transaction has committed, so orders.placed only counts orders
    // that are really in the database.
    public PlacementResult place(Long userId, String idempotencyKey, PlaceOrderRequest request) {
        Timer.Sample sample = metrics.startPlacement();
        PlacementResult result;
        try {
            result = placeWithRetries(userId, idempotencyKey, request);
        } catch (RuntimeException ex) {
            metrics.placementFailed(sample, ex);
            throw ex;
        }
        metrics.placementFinished(sample, result);
        return result;
    }

    // Not @Transactional: each attempt needs its own transaction, and the order number is taken
    // before the transaction opens, so one request never holds two pooled connections at once.
    private PlacementResult placeWithRetries(Long userId, String idempotencyKey, PlaceOrderRequest request) {
        String key = validKey(idempotencyKey);
        String requestHash = Sha256.hex(objectMapper.writeValueAsString(request));
        for (int attempt = 1; ; attempt++) {
            Optional<StoredResponse> stored = keyStore.find(userId, key);
            if (stored.isPresent()) {
                return replay(stored.get(), requestHash);
            }
            String orderNumber = orderNumberGenerator.next();
            try {
                return new PlacementResult.Created(transactionTemplate.execute(status ->
                        placeOnce(userId, key, requestHash, orderNumber, request)));
            } catch (DuplicateKeyException | ConcurrencyFailureException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                log.info("Retrying order placement for user {} after a concurrent request ({})", userId,
                        ex.getClass().getSimpleName());
            }
        }
    }

    private OrderResponse placeOnce(Long userId, String key, String requestHash, String orderNumber,
                                    PlaceOrderRequest request) {
        keyStore.claim(userId, key, requestHash);
        OrderResponse order = orderService.create(userId, orderNumber, request);
        keyStore.complete(userId, key, HttpStatus.CREATED.value(), objectMapper.writeValueAsString(order));
        return order;
    }

    private PlacementResult replay(StoredResponse stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleViolationException(KEY_REUSED,
                    "This Idempotency-Key was already used with a different request body");
        }
        return new PlacementResult.Replayed(stored.status(), objectMapper.readValue(stored.body(), OrderResponse.class));
    }

    private static String validKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidRequestException(KEY_MISSING, "The Idempotency-Key header is required");
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new InvalidRequestException(KEY_INVALID,
                    "The Idempotency-Key header must be 1 to 100 letters, digits or . _ : -");
        }
        return key;
    }
}
