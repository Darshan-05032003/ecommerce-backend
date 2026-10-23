package com.darshan.ecommerce.common.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Highest precedence makes this the outermost advice, so the measured time includes the work of
// the other proxies on the same bean, such as the transaction commit.
// Arguments are never logged: order and payment methods receive addresses and user ids.
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExecutionTimeAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeAspect.class);

    private final Clock clock;
    private final Duration threshold;

    public ExecutionTimeAspect(Clock clock, ObservabilityProperties properties) {
        this.clock = clock;
        this.threshold = properties.slowMethodThreshold();
    }

    @Around("@within(com.darshan.ecommerce.common.observability.LogExecutionTime)"
            + " || @annotation(com.darshan.ecommerce.common.observability.LogExecutionTime)")
    public Object logIfSlow(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = clock.instant();
        try {
            return joinPoint.proceed();
        } finally {
            Duration elapsed = Duration.between(start, clock.instant());
            if (elapsed.compareTo(threshold) > 0) {
                log.warn("Slow call {} took {} ms (threshold {} ms)", methodName(joinPoint), elapsed.toMillis(),
                        threshold.toMillis());
            }
        }
    }

    private static String methodName(ProceedingJoinPoint joinPoint) {
        return AopUtils.getTargetClass(joinPoint.getTarget()).getSimpleName() + "." + joinPoint.getSignature().getName();
    }
}
