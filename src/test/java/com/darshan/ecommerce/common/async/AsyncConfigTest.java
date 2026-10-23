package com.darshan.ecommerce.common.async;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Properties.class, AsyncConfig.class)
            .withPropertyValues("app.async.core-size=2", "app.async.max-size=4", "app.async.queue-capacity=50",
                    "app.async.thread-name-prefix=mail-", "app.async.rejection-policy=CALLER_RUNS",
                    "app.async.shutdown-timeout=10s");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AsyncProperties.class)
    static class Properties {
    }

    @Test
    void theExecutorIsBuiltFromTheConfiguration() {
        runner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(50);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("mail-");
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        });
    }

    @Test
    void aMaximumBelowTheCoreSizeIsRejectedAtStartup() {
        runner.withPropertyValues("app.async.core-size=8", "app.async.max-size=2")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("maxSize must not be smaller than coreSize"));
    }

    @Test
    void anUnknownRejectionPolicyIsRejectedAtStartup() {
        runner.withPropertyValues("app.async.rejection-policy=RETRY")
                .run(context -> assertThat(context).hasFailed());
    }
}
