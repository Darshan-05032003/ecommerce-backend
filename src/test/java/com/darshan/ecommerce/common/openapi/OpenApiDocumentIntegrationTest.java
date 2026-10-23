package com.darshan.ecommerce.common.openapi;

import com.darshan.ecommerce.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiDocumentIntegrationTest {

    private static final String PLACE_ORDER = "$.paths['/api/v1/me/orders'].post";
    private static final String LOGIN = "$.paths['/api/v1/auth/login'].post";
    private static final String RESPONSES = "$.components.responses";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentAndSwaggerUiAreReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void declaresTheBearerSchemeAndAppliesItGlobally() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void publicEndpointsOptOutOfTheBearerScheme() throws Exception {
        apiDocs()
                .andExpect(jsonPath(LOGIN + ".security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get.security", empty()))
                .andExpect(jsonPath(PLACE_ORDER + ".security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders'].get.security").doesNotExist());
    }

    @Test
    void groupsOperationsUnderTheDeclaredTags() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.tags[*].name",
                        contains("Auth", "Catalog", "Categories", "Cart", "Orders", "Payments", "Admin")))
                .andExpect(jsonPath(LOGIN + ".tags", contains("Auth")))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.tags", contains("Catalog")))
                .andExpect(jsonPath("$.paths['/api/v1/me/cart'].get.tags", contains("Cart")))
                .andExpect(jsonPath(PLACE_ORDER + ".tags", contains("Orders")))
                .andExpect(jsonPath("$.paths['/api/v1/me/orders/{orderId}/payments'].post.tags", contains("Payments")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/categories'].post.tags", contains("Admin")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders/{id}/status'].patch.tags", contains("Admin")));
    }

    @Test
    void documentsTheIdempotencyKeyHeaderAsRequired() throws Exception {
        apiDocs()
                .andExpect(jsonPath(PLACE_ORDER + ".parameters[0].name").value("Idempotency-Key"))
                .andExpect(jsonPath(PLACE_ORDER + ".parameters[0].in").value("header"))
                .andExpect(jsonPath(PLACE_ORDER + ".parameters[0].required").value(true))
                .andExpect(jsonPath(PLACE_ORDER + ".responses['201'].headers['Idempotent-Replayed']").exists());
    }

    @Test
    void referencesSharedProblemResponsesWithExamples() throws Exception {
        apiDocs()
                .andExpect(jsonPath(PLACE_ORDER + ".responses['422']['$ref']")
                        .value("#/components/responses/UnprocessableContent"))
                .andExpect(jsonPath(RESPONSES + ".UnprocessableContent.content['application/problem+json']"
                        + ".examples.outOfStock.value.errorCode").value("OUT_OF_STOCK"))
                .andExpect(jsonPath(RESPONSES + ".BadRequest.content['application/problem+json']"
                        + ".examples.validation.value.errors[0].field").value("email"))
                .andExpect(jsonPath(RESPONSES + ".NotFound.content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/Problem"))
                .andExpect(jsonPath(LOGIN + ".responses['429']['$ref']").value("#/components/responses/TooManyRequests"))
                .andExpect(jsonPath(RESPONSES + ".TooManyRequests.headers['Retry-After']").exists());
    }

    @Test
    void addsStandardErrorsFromHowAnEndpointIsDeclared() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders'].get.responses['403']['$ref']")
                        .value("#/components/responses/Forbidden"))
                .andExpect(jsonPath("$.paths['/api/v1/me/cart'].get.responses['401']['$ref']")
                        .value("#/components/responses/Unauthorized"))
                .andExpect(jsonPath("$.paths['/api/v1/me/cart'].get.responses['403']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/me/cart'].get.responses['400']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['400']['$ref']")
                        .value("#/components/responses/BadRequest"));
    }

    private ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
