package com.darshan.ecommerce.common.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final AsyncProperties properties;

    public AsyncConfig(AsyncProperties properties) {
        this.properties = properties;
    }

    // Shutdown waits for queued tasks (up to shutdownTimeout) so that emails already accepted
    // are not dropped by a normal restart.
    @Bean
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.coreSize());
        executor.setMaxPoolSize(properties.maxSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setRejectedExecutionHandler(rejectionHandler(properties.rejectionPolicy()));
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(properties.shutdownTimeout().toMillis());
        return executor;
    }

    // Called on this @Configuration's proxy, so it returns the singleton bean above.
    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncExceptionHandler();
    }

    private static RejectedExecutionHandler rejectionHandler(AsyncProperties.RejectionPolicy policy) {
        return switch (policy) {
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case DISCARD -> (task, executor) -> log.warn("Async task discarded: queue of {} is full",
                    executor.getQueue().size());
        };
    }
}
