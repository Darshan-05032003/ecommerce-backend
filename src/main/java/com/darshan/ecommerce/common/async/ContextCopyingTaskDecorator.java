package com.darshan.ecommerce.common.async;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

// MDC values and the security context live in ThreadLocals, so a task handed to a pool thread
// would lose the correlation id and the caller's identity. This captures both on the submitting
// thread and installs them around the task. Afterwards the running thread gets back what it had
// before: nothing for a reused pool thread (so the next task cannot run as the previous user),
// and its own request context when CALLER_RUNS made the submitting thread run the task itself.
public class ContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        SecurityContext callerContext = SecurityContextHolder.getContext();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            SecurityContext previousContext = SecurityContextHolder.getContext();
            install(callerMdc);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(callerContext.getAuthentication());
            SecurityContextHolder.setContext(context);
            try {
                task.run();
            } finally {
                install(previousMdc);
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    private static void install(Map<String, String> mdc) {
        if (mdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdc);
        }
    }
}
