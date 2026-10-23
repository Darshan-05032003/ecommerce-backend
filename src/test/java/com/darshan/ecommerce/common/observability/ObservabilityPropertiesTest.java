package com.darshan.ecommerce.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsTo500Milliseconds() {
        runner.run(context -> assertThat(context.getBean(ObservabilityProperties.class).slowMethodThreshold())
                .isEqualTo(Duration.ofMillis(500)));
    }

    @Test
    void acceptsAnOverride() {
        runner.withPropertyValues("SLOW_METHOD_THRESHOLD=2s")
                .run(context -> assertThat(context.getBean(ObservabilityProperties.class).slowMethodThreshold())
                        .isEqualTo(Duration.ofSeconds(2)));
    }

    @Test
    void rejectsAZeroThreshold() {
        runner.withPropertyValues("app.observability.slow-method-threshold=0ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsAThresholdAboveOneMinute() {
        runner.withPropertyValues("app.observability.slow-method-threshold=2m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ObservabilityProperties.class)
    static class PropertiesConfig {
    }
}
