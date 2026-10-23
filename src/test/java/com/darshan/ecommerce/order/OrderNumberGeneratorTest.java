package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.persistence.PersistenceTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// The generator opens its own transaction, so the slice's test transaction is switched off and
// the counter table is reset explicitly between tests.
@PersistenceTest
@Import(OrderNumberGenerator.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderNumberGeneratorTest {

    private static final int THREADS = 20;

    @Autowired
    private OrderNumberGenerator generator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetCounter() {
        jdbcTemplate.update("DELETE FROM order_number_sequence");
    }

    @Test
    void producesSequentialNumbersPrefixedWithTheClockYear() {
        assertThat(generator.next()).isEqualTo("ORD-2026-000001");
        assertThat(generator.next()).isEqualTo("ORD-2026-000002");
    }

    @Test
    void staysUniqueUnderConcurrentCalls() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return generator.next();
                }));
            }
            startGate.countDown();

            List<String> numbers = new ArrayList<>();
            for (Future<String> future : futures) {
                numbers.add(future.get(30, TimeUnit.SECONDS));
            }

            List<String> expected = IntStream.rangeClosed(1, THREADS)
                    .mapToObj(n -> "ORD-2026-%06d".formatted(n))
                    .toList();
            assertThat(numbers).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        } finally {
            executor.shutdownNow();
        }
    }
}
