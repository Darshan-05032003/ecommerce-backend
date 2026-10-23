package com.darshan.ecommerce.common.observability;

import com.darshan.ecommerce.MutableClock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A small context with only the aspect and two test beans. The beans advance a mutable clock to
// simulate how long they take, so the test is exact and never sleeps.
@SpringJUnitConfig(ExecutionTimeAspectTest.Config.class)
class ExecutionTimeAspectTest {

    private static final Duration THRESHOLD = Duration.ofMillis(500);

    @Autowired
    private TimedService timedService;

    @Autowired
    private PartlyTimedService partlyTimedService;

    @Autowired
    private MutableClock clock;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger aspectLogger = (Logger) LoggerFactory.getLogger(ExecutionTimeAspect.class);

    @BeforeEach
    void captureLogs() {
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        aspectLogger.detachAppender(appender);
    }

    @Test
    void annotatedBeansAreProxied() {
        assertThat(AopUtils.isAopProxy(timedService)).isTrue();
        assertThat(AopUtils.isCglibProxy(timedService)).isTrue();
    }

    @Test
    void logsACallSlowerThanTheThresholdWithMethodNameAndDuration() {
        timedService.work(clock, Duration.ofMillis(750), "4111111111111111");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Slow call TimedService.work took 750 ms (threshold 500 ms)")
                    .doesNotContain("4111111111111111");
        });
    }

    @Test
    void doesNotLogACallAtOrBelowTheThreshold() {
        timedService.work(clock, Duration.ofMillis(120), "x");
        timedService.work(clock, THRESHOLD, "x");

        assertThat(appender.list).isEmpty();
    }

    @Test
    void logsASlowCallThatThrowsAndRethrowsTheException() {
        assertThatThrownBy(() -> timedService.failAfter(clock, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gateway down");

        assertThat(appender.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("Slow call TimedService.failAfter took 2000 ms (threshold 500 ms)");
    }

    @Test
    void methodLevelAnnotationOnlyTimesThatMethod() {
        partlyTimedService.untimed(clock, Duration.ofSeconds(1));
        assertThat(appender.list).isEmpty();

        partlyTimedService.timed(clock, Duration.ofSeconds(1));
        assertThat(appender.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("Slow call PartlyTimedService.timed took 1000 ms (threshold 500 ms)");
    }

    @Test
    void selfInvocationBypassesTheProxy() {
        partlyTimedService.callsTimedInternally(clock, Duration.ofSeconds(1));

        assertThat(appender.list).isEmpty();
    }

    @LogExecutionTime
    static class TimedService {

        void work(MutableClock clock, Duration takes, String cardNumber) {
            clock.advance(takes);
        }

        void failAfter(MutableClock clock, Duration takes) {
            clock.advance(takes);
            throw new IllegalStateException("gateway down");
        }
    }

    static class PartlyTimedService {

        @LogExecutionTime
        public void timed(MutableClock clock, Duration takes) {
            clock.advance(takes);
        }

        public void untimed(MutableClock clock, Duration takes) {
            clock.advance(takes);
        }

        public void callsTimedInternally(MutableClock clock, Duration takes) {
            timed(clock, takes);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-03-15T10:15:30Z"));
        }

        @Bean
        ExecutionTimeAspect executionTimeAspect(MutableClock clock) {
            return new ExecutionTimeAspect(clock, new ObservabilityProperties(THRESHOLD));
        }

        @Bean
        TimedService timedService() {
            return new TimedService();
        }

        @Bean
        PartlyTimedService partlyTimedService() {
            return new PartlyTimedService();
        }
    }
}
