package com.darshan.ecommerce.common.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

// A void @Async method has no caller left to receive its exception, so without this handler a
// failure would only reach the executor's default output. Runs on the pool thread, where the
// decorator has already installed the correlation id. Only the exception type is logged: mail
// exceptions put the failed recipient addresses in their message.
public class LoggingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingAsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Async task {}.{} failed: {}", method.getDeclaringClass().getSimpleName(), method.getName(),
                ex.getClass().getSimpleName());
    }
}
