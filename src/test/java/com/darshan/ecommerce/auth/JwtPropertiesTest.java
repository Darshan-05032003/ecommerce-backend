package com.darshan.ecommerce.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static final String STRONG_SECRET = "a-secret-that-is-long-enough-for-hs256-signing";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void prodProfileFailsToStartWithoutJwtSecret() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rejectedFields(context)).containsOnly("secret");
        });
    }

    @Test
    void prodProfileRejectsSecretShorterThan32Characters() {
        runner.withPropertyValues("spring.profiles.active=prod", "JWT_SECRET=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rejectedFields(context)).containsOnly("secret");
        });
    }

    @Test
    void prodProfileStartsWhenJwtSecretIsProvided() {
        runner.withPropertyValues("spring.profiles.active=prod", "JWT_SECRET=" + STRONG_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.secret()).isEqualTo(STRONG_SECRET);
            assertThat(properties.issuer()).isEqualTo("ecommerce-backend");
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    void withoutAnActiveProfileThereIsNoDefaultSecretEither() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(rejectedFields(context)).containsOnly("secret");
        });
    }

    @Test
    void devProfileStartsWithALocalDefaultSecret() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).secret()).hasSizeGreaterThanOrEqualTo(32);
        });
    }

    @Test
    void devProfileStartsWithTheVariablesFromEnvExample() throws IOException {
        // Compose passes a copied .env.example to the container, and an empty JWT_SECRET there
        // would replace the dev default instead of falling back to it.
        List<String> variables = Files.readAllLines(Path.of(".env.example")).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        String[] properties = Stream.concat(Stream.of("spring.profiles.active=dev"), variables.stream())
                .toArray(String[]::new);

        runner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).secret()).hasSizeGreaterThanOrEqualTo(32);
        });
    }

    private static List<String> rejectedFields(AssertableApplicationContext context) {
        Throwable rootCause = NestedExceptionUtils.getRootCause(context.getStartupFailure());
        assertThat(rootCause).isInstanceOf(BindValidationException.class);
        return ((BindValidationException) rootCause).getValidationErrors().getAllErrors().stream()
                .map(FieldError.class::cast)
                .map(FieldError::getField)
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }
}
