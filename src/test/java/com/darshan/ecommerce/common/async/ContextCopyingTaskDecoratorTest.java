package com.darshan.ecommerce.common.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCopyingTaskDecoratorTest {

    private record Seen(String thread, String correlationId, String user) {
    }

    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @BeforeEach
    void startExecutor() {
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("test-async-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void cleanUp() {
        executor.shutdown();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theCorrelationIdAndTheCallerReachThePoolThread() throws Exception {
        MDC.put("correlationId", "req-42");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice@example.com", null));

        Seen seen = executor.submit(ContextCopyingTaskDecoratorTest::observe).get(5, TimeUnit.SECONDS);

        assertThat(seen.thread()).startsWith("test-async-");
        assertThat(seen.correlationId()).isEqualTo("req-42");
        assertThat(seen.user()).isEqualTo("alice@example.com");
    }

    @Test
    void aReusedPoolThreadDoesNotKeepThePreviousCallersContext() throws Exception {
        MDC.put("correlationId", "req-1");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice@example.com", null));
        executor.submit(ContextCopyingTaskDecoratorTest::observe).get(5, TimeUnit.SECONDS);

        MDC.clear();
        SecurityContextHolder.clearContext();
        Seen next = executor.submit(ContextCopyingTaskDecoratorTest::observe).get(5, TimeUnit.SECONDS);

        assertThat(next.correlationId()).isNull();
        assertThat(next.user()).isNull();
    }

    // With the CALLER_RUNS rejection policy the submitting thread runs the decorated task itself;
    // it must get its own request context back afterwards.
    @Test
    void aTaskRunOnTheSubmittingThreadRestoresThatThreadsContext() {
        MDC.put("correlationId", "req-7");
        Authentication bob = new TestingAuthenticationToken("bob@example.com", null);
        SecurityContextHolder.getContext().setAuthentication(bob);
        Runnable decorated = new ContextCopyingTaskDecorator().decorate(() -> MDC.put("correlationId", "changed"));

        decorated.run();

        assertThat(MDC.get("correlationId")).isEqualTo("req-7");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(bob);
    }

    private static Seen observe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return new Seen(Thread.currentThread().getName(), MDC.get("correlationId"),
                authentication == null ? null : authentication.getName());
    }
}
