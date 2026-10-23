package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.crypto.Sha256;
import com.darshan.ecommerce.common.exception.InvalidRequestException;
import com.darshan.ecommerce.order.IdempotencyKeyStore.StoredResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    private static final Long USER_ID = 7L;
    private static final String KEY = "checkout-1";
    private static final PlaceOrderRequest REQUEST = new PlaceOrderRequest("1 Main Street");

    @Mock
    private OrderService orderService;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private IdempotencyKeyStore keyStore;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private OrderPlacementService placementService;

    @BeforeEach
    void setUp() {
        placementService = new OrderPlacementService(orderService, orderNumberGenerator, keyStore, objectMapper,
                transactionManager, new OrderMetrics(registry));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(orderNumberGenerator.next()).thenReturn("ORD-2026-000001", "ORD-2026-000002", "ORD-2026-000003");
    }

    // The losing request of a same-key race gets a duplicate key on its INSERT, starts over, and
    // then finds the winner's committed response.
    @Test
    void aDuplicateKeyIsRetriedAndThenReplaysTheStoredResponse() {
        OrderResponse winner = new OrderResponse(1L, "ORD-2026-000009", USER_ID, OrderStatus.CREATED,
                new BigDecimal("40.00"), "1 Main Street", null, List.of());
        when(keyStore.find(USER_ID, KEY)).thenReturn(Optional.empty(), Optional.of(
                new StoredResponse(requestHash(), 201, objectMapper.writeValueAsString(winner))));
        when(orderService.create(eq(USER_ID), anyString(), eq(REQUEST)))
                .thenThrow(new DuplicateKeyException("uk_idempotency_keys_user_key"));

        PlacementResult result = placementService.place(USER_ID, KEY, REQUEST);

        assertThat(result).isEqualTo(new PlacementResult.Replayed(201, winner));
        verify(orderNumberGenerator, times(1)).next();
        verify(transactionManager).rollback(any());
        assertThat(placements("replayed")).isEqualTo(1);
    }

    @Test
    void givesUpAfterThreeConcurrencyFailuresAndCountsAFailedPlacement() {
        when(keyStore.find(USER_ID, KEY)).thenReturn(Optional.empty());
        CannotAcquireLockException deadlock = new CannotAcquireLockException("Deadlock found");
        when(orderService.create(eq(USER_ID), anyString(), eq(REQUEST))).thenThrow(deadlock);

        assertThatThrownBy(() -> placementService.place(USER_ID, KEY, REQUEST)).isSameAs(deadlock);

        verify(orderNumberGenerator, times(OrderPlacementService.MAX_ATTEMPTS)).next();
        verify(transactionManager, times(OrderPlacementService.MAX_ATTEMPTS)).rollback(any());
        assertThat(placements("failed")).isEqualTo(1);
        assertThat(registry.get("orders.placed").counter().count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("wellFormedKeys")
    void acceptsKeysOfUpToOneHundredAllowedCharacters(String key) {
        when(keyStore.find(USER_ID, key)).thenReturn(Optional.empty());

        assertThatCode(() -> placementService.place(USER_ID, key, REQUEST)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("malformedKeys")
    void rejectsMalformedKeysBeforeTouchingTheDatabase(String key) {
        assertThatThrownBy(() -> placementService.place(USER_ID, key, REQUEST))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("errorCode").isEqualTo(OrderPlacementService.KEY_INVALID);

        verifyNoInteractions(keyStore, orderNumberGenerator, orderService);
        assertThat(placements("rejected")).isEqualTo(1);
    }

    static Stream<String> wellFormedKeys() {
        return Stream.of("a", "order-2026.03.15_checkout:7", "k".repeat(100));
    }

    static Stream<String> malformedKeys() {
        return Stream.of("has space", "semi;colon", "\u00fcmlaut", "k".repeat(101));
    }

    private String requestHash() {
        return Sha256.hex(objectMapper.writeValueAsString(REQUEST));
    }

    private long placements(String outcome) {
        return registry.get("orders.placement").tag("outcome", outcome).timer().count();
    }
}
