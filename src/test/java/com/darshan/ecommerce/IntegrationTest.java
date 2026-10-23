package com.darshan.ecommerce;

import com.darshan.ecommerce.common.persistence.TestClockConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Full context on Testcontainers MySQL and Redis with MockMvc, a mutable clock and an in-memory
// mail sender. Every class using this annotation shares one cached context, so the containers
// start once for all of them.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class, TestClockConfiguration.class,
        IntegrationTestSupport.class, RecordingMailSender.Config.class})
@ActiveProfiles("test")
public @interface IntegrationTest {
}
