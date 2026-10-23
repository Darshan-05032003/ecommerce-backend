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

// Full context on Testcontainers MySQL with MockMvc and a mutable clock. Every class using this
// annotation shares one cached context, so the MySQL container starts once for all of them.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, IntegrationTestSupport.class})
@ActiveProfiles("test")
public @interface IntegrationTest {
}
