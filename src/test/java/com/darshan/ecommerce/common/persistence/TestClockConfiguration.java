package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.MutableClock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Instant;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-03-15T10:15:30Z");

    @Bean
    MutableClock clock() {
        return new MutableClock(START);
    }
}
