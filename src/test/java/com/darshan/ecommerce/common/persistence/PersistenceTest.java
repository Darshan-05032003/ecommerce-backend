package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.TestcontainersConfiguration;
import com.darshan.ecommerce.common.config.JpaConfig;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Repository slice on the shared MySQL container with Flyway migrations, auditing and a test clock.
// The container is also used by the integration tests, which commit their data, so every test
// first empties the tables. The script runs inside the test transaction and is rolled back with it.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class, TestClockConfiguration.class})
@ActiveProfiles("test")
@Sql("/clean-database.sql")
public @interface PersistenceTest {
}
