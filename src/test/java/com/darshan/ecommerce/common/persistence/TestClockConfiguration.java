package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.MutableClock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

// @Primary so that in a full @SpringBootTest this clock wins over ClockConfig's system clock.
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-03-15T10:15:30Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }
}
