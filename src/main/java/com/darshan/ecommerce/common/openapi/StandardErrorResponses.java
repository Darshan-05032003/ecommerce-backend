package com.darshan.ecommerce.common.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.TreeMap;

// Adds the error responses that follow from how an endpoint is declared (it takes input, needs a
// token, needs a role), so controllers only list the ones that come from their business rules.
class StandardErrorResponses implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Map<String, ApiResponse> responses = new TreeMap<>(operation.getResponses());
        if (takesInput(operation)) {
            responses.putIfAbsent("400", ApiErrorResponses.ref(ApiErrorResponses.BAD_REQUEST));
        }
        if (requiresToken(operation)) {
            responses.putIfAbsent("401", ApiErrorResponses.ref(ApiErrorResponses.UNAUTHORIZED));
        }
        if (requiresRole(handlerMethod)) {
            responses.putIfAbsent("403", ApiErrorResponses.ref(ApiErrorResponses.FORBIDDEN));
        }
        ApiResponses sorted = new ApiResponses();
        sorted.putAll(responses);
        return operation.responses(sorted);
    }

    private static boolean takesInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    // A null list means the global bearer requirement applies; public endpoints declare an empty one.
    private static boolean requiresToken(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }

    private static boolean requiresRole(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }
}
