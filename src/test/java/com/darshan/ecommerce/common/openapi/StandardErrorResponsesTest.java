package com.darshan.ecommerce.common.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardErrorResponsesTest {

    private final StandardErrorResponses customizer = new StandardErrorResponses();

    @Test
    void publicOperationWithoutInputGetsNoStandardErrors() throws Exception {
        Operation operation = operation().security(List.of()).parameters(List.of());

        customizer.customize(operation, handler(PlainController.class, "plain"));

        assertThat(operation.getResponses()).containsOnlyKeys("200");
    }

    @Test
    void requestBodyMeansBadRequestAndInheritedSecurityMeansUnauthorized() throws Exception {
        Operation operation = operation().requestBody(new RequestBody());

        customizer.customize(operation, handler(PlainController.class, "plain"));

        assertThat(operation.getResponses()).containsOnlyKeys("200", "400", "401");
        assertThat(operation.getResponses().get("400").get$ref()).isEqualTo("#/components/responses/BadRequest");
        assertThat(operation.getResponses().get("401").get$ref()).isEqualTo("#/components/responses/Unauthorized");
    }

    @Test
    void explicitSecurityRequirementStillMeansUnauthorized() throws Exception {
        Operation operation = operation().addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH));

        customizer.customize(operation, handler(PlainController.class, "plain"));

        assertThat(operation.getResponses()).containsKey("401");
    }

    @Test
    void preAuthorizeOnTheMethodOrTheClassMeansForbidden() throws Exception {
        Operation onMethod = operation();
        Operation onClass = operation();

        customizer.customize(onMethod, handler(PlainController.class, "adminOnly"));
        customizer.customize(onClass, handler(AdminController.class, "anything"));

        assertThat(onMethod.getResponses()).containsKey("403");
        assertThat(onClass.getResponses()).containsKey("403");
    }

    @Test
    void keepsResponsesTheControllerDeclaredAndSortsByStatus() throws Exception {
        ApiResponse specific = new ApiResponse().description("declared by the controller");
        Operation operation = operation().parameters(List.of(new Parameter().in("query").name("page")));
        operation.getResponses().addApiResponse("422", new ApiResponse().$ref(ApiErrorResponses.UNPROCESSABLE));
        operation.getResponses().addApiResponse("400", specific);

        customizer.customize(operation, handler(PlainController.class, "plain"));

        assertThat(operation.getResponses().get("400")).isSameAs(specific);
        assertThat(operation.getResponses().keySet()).containsExactly("200", "400", "401", "422");
    }

    private static Operation operation() {
        return new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
    }

    private static HandlerMethod handler(Class<?> type, String method) throws ReflectiveOperationException {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getDeclaredMethod(method));
    }

    static class PlainController {

        void plain() {
        }

        @PreAuthorize("hasRole('ADMIN')")
        void adminOnly() {
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    static class AdminController {

        void anything() {
        }
    }
}
