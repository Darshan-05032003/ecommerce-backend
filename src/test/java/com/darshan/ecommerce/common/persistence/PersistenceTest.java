package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.TestcontainersConfiguration;
import com.darshan.ecommerce.common.config.JpaConfig;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Repository slice on the real MySQL image with Flyway migrations, auditing and a test clock.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class, TestClockConfiguration.class})
@ActiveProfiles("test")
public @interface PersistenceTest {
}
