package com.darshan.ecommerce.common.observability;

import com.darshan.ecommerce.common.web.CorrelationIdFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Logback is configured once per JVM, before any test context starts, so a @SpringBootTest with
// the prod profile would still log with the test configuration. Instead this test builds Boot's
// StructuredLogEncoder against the same YAML files the prod profile uses and encodes one event.
class StructuredLoggingTest {

    private final LoggerContext loggerContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();

    @BeforeEach
    void startEncoderWithTheProdConfiguration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(yaml("application.yml"));
        environment.getPropertySources().addFirst(yaml("application-prod.yml"));
        loggerContext.putObject(Environment.class.getName(), environment);

        encoder.setContext(loggerContext);
        encoder.setFormat(environment.getRequiredProperty("logging.structured.format.console"));
        encoder.start();
    }

    @AfterEach
    void stopEncoder() {
        encoder.stop();
        loggerContext.stop();
    }

    @Test
    void prodWritesOneEcsJsonObjectPerLineWithTheCorrelationId() {
        LoggingEvent event = new LoggingEvent("com.darshan.ecommerce.payment.PaymentService",
                loggerContext.getLogger("com.darshan.ecommerce.payment.PaymentService"), Level.INFO,
                "Payment {} for order {} declined", null, new Object[] {7, 42});
        event.setMDCPropertyMap(Map.of(CorrelationIdFilter.MDC_KEY, "corr-123"));
        event.setThreadName("http-nio-8081-exec-1");

        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
        JsonNode json = JsonMapper.builder().build().readTree(line);

        assertThat(line).endsWith("\n").doesNotContain("\n{");
        assertThat(json.get("correlationId").asString()).isEqualTo("corr-123");
        assertThat(json.get("message").asString()).isEqualTo("Payment 7 for order 42 declined");
        assertThat(json.get("log").get("level").asString()).isEqualTo("INFO");
        assertThat(json.get("log").get("logger").asString()).isEqualTo("com.darshan.ecommerce.payment.PaymentService");
        assertThat(json.get("service").get("name").asString()).isEqualTo("ecommerce-backend");
        assertThat(json.get("service").get("environment").asString()).isEqualTo("prod");
        assertThat(json.get("ecs").get("version").asString()).isNotBlank();
        assertThat(json.has("@timestamp")).isTrue();
    }

    @Test
    void devAndTheDefaultConfigurationKeepTheReadableConsolePattern() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(yaml("application.yml"));
        environment.getPropertySources().addFirst(yaml("application-dev.yml"));

        assertThat(environment.containsProperty("logging.structured.format.console")).isFalse();
        assertThat(environment.getProperty("logging.pattern.correlation")).isEqualTo("[%X{correlationId:-}] ");
    }

    private static PropertySource<?> yaml(String name) throws IOException {
        return new YamlPropertySourceLoader().load(name, new ClassPathResource(name)).getFirst();
    }
}
