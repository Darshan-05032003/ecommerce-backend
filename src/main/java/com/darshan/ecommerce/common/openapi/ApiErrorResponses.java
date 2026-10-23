package com.darshan.ecommerce.common.openapi;

import com.darshan.ecommerce.common.exception.FieldViolation;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reusable error responses for the OpenAPI document. The examples mirror the bodies the API
// really returns (GlobalExceptionHandler and the security entry points), including which
// statuses carry an errorCode.
public final class ApiErrorResponses {

    public static final String BAD_REQUEST = "BadRequest";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String NOT_FOUND = "NotFound";
    public static final String CONFLICT = "Conflict";
    public static final String UNPROCESSABLE = "UnprocessableContent";
    public static final String TOO_MANY_REQUESTS = "TooManyRequests";
    public static final String GATEWAY_TIMEOUT = "GatewayTimeout";

    static final String PROBLEM_SCHEMA = "Problem";

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String CORRELATION_ID = "3c2d754f-6f0f-42d8-8240-5acc50746cd5";
    private static final String TIMESTAMP = "2026-10-23T11:56:00.937Z";

    private ApiErrorResponses() {
    }

    static ApiResponse ref(String name) {
        return new ApiResponse().$ref(name);
    }

    static Schema<?> problemSchema() {
        Schema<?> violation = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        return new ObjectSchema()
                .description("RFC 9457 problem detail")
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().description("Request path"))
                .addProperty("correlationId", new StringSchema()
                        .description("Same value as the X-Correlation-Id response header"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("errorCode", new StringSchema()
                        .description("Stable code for clients to branch on. Sent with business rule failures "
                                + "(422), request errors detected by the application (400) and payment provider "
                                + "errors (502, 504)"))
                .addProperty("errors", new ArraySchema().items(violation)
                        .description("Rejected fields; only on validation failures"));
    }

    static Map<String, ApiResponse> all() {
        Map<String, ApiResponse> responses = new LinkedHashMap<>();
        responses.put(BAD_REQUEST, response("The request is malformed or fails validation",
                example("validation", "Bean Validation failed",
                        withErrors(problem(400, "Request validation failed", "/api/v1/auth/register"),
                                new FieldViolation("email", "must be a well-formed email address"),
                                new FieldViolation("fullName", "must not be blank"))),
                example("idempotencyKeyMissing", "POST /api/v1/me/orders without an Idempotency-Key",
                        withErrorCode(problem(400, "The Idempotency-Key header is required", "/api/v1/me/orders"),
                                "IDEMPOTENCY_KEY_MISSING"))));
        responses.put(UNAUTHORIZED, response("The bearer token is missing, invalid or expired, or the credentials are wrong",
                example("missingToken", "No Authorization header",
                        problem(401, "Authentication required", "/api/v1/me/cart")),
                example("badCredentials", "Unknown email or wrong password",
                        problem(401, "Invalid email or password", "/api/v1/auth/login"))));
        responses.put(FORBIDDEN, response("The caller does not have the required role",
                example("notAdmin", "A customer calling an admin endpoint",
                        problem(403, "Access denied", "/api/v1/admin/orders"))));
        responses.put(NOT_FOUND, response("The resource does not exist or belongs to another user",
                example("orderNotFound", "Another user's order looks the same as a missing one",
                        problem(404, "Order not found with id 42", "/api/v1/me/orders/42"))));
        responses.put(CONFLICT, response("The request conflicts with the current state of the resource",
                example("invalidTransition", "Order status change not allowed",
                        problem(409, "Order cannot move from SHIPPED to CANCELLED", "/api/v1/admin/orders/42/status")),
                example("concurrentUpdate", "Optimistic lock: another request saved first",
                        problem(409, "The resource was modified by another request, reload it and retry",
                                "/api/v1/admin/products/4"))));
        responses.put(UNPROCESSABLE, response("The request is well formed but breaks a business rule",
                example("outOfStock", "Not enough stock when the order is placed",
                        withErrorCode(problem(422, "Not enough stock left for product 4", "/api/v1/me/orders"),
                                "OUT_OF_STOCK")),
                example("idempotencyKeyReused", "Same Idempotency-Key with a different body",
                        withErrorCode(problem(422, "This Idempotency-Key was already used with a different request body",
                                "/api/v1/me/orders"), "IDEMPOTENCY_KEY_REUSED")),
                example("paymentDeclined", "The payment provider declined the payment",
                        withErrorCode(problem(422, "The payment was declined: Declined by the issuer",
                                "/api/v1/me/orders/42/payments"), "PAYMENT_DECLINED"))));
        responses.put(TOO_MANY_REQUESTS, response("Too many login attempts for this client and email",
                example("loginRateLimited", "Sixth login attempt within a minute",
                        problem(429, "Too many login attempts, try again later", "/api/v1/auth/login")))
                .addHeaderObject("Retry-After", new Header()
                        .description("Seconds to wait before the next attempt")
                        .schema(new IntegerSchema())));
        responses.put(GATEWAY_TIMEOUT, response("The payment provider did not answer in time",
                example("paymentTimeout", "The order stays CREATED and can be paid again",
                        withErrorCode(problem(504, "The payment provider did not answer in time; the order was not paid",
                                "/api/v1/me/orders/42/payments"), "PAYMENT_TIMEOUT"))));
        return responses;
    }

    @SafeVarargs
    private static ApiResponse response(String description, Map.Entry<String, Example>... examples) {
        Map<String, Example> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Example> example : examples) {
            ordered.put(example.getKey(), example.getValue());
        }
        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA)).examples(ordered);
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON, mediaType));
    }

    private static Map.Entry<String, Example> example(String name, String summary, Map<String, Object> value) {
        return Map.entry(name, new Example().summary(summary).value(value));
    }

    private static Map<String, Object> problem(int status, String detail, String instance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", HttpStatus.valueOf(status).getReasonPhrase());
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", instance);
        body.put("correlationId", CORRELATION_ID);
        body.put("timestamp", TIMESTAMP);
        return body;
    }

    private static Map<String, Object> withErrorCode(Map<String, Object> problem, String errorCode) {
        problem.put("errorCode", errorCode);
        return problem;
    }

    private static Map<String, Object> withErrors(Map<String, Object> problem, FieldViolation... violations) {
        problem.put("errors", List.of(violations));
        return problem;
    }
}
