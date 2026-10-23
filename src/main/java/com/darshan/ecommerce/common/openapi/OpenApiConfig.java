package com.darshan.ecommerce.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI ecommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-commerce API")
                        .version("v1")
                        .description("Catalogue, cart, idempotent ordering and payments. Log in with "
                                + "POST /api/v1/auth/login and send the access token as a bearer token. "
                                + "Every error is an RFC 9457 problem detail."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login, valid for 15 minutes"))
                        .addSchemas(ApiErrorResponses.PROBLEM_SCHEMA, ApiErrorResponses.problemSchema())
                        .responses(ApiErrorResponses.all()))
                // Applies to every operation; the public controllers opt out with an empty @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .tags(List.of(
                        tag(ApiTags.AUTH, "Registration, login, token rotation and the caller's profile"),
                        tag(ApiTags.CATALOG, "Public product catalogue"),
                        tag(ApiTags.CATEGORIES, "Public product categories"),
                        tag(ApiTags.CART, "The caller's shopping cart"),
                        tag(ApiTags.ORDERS, "The caller's orders"),
                        tag(ApiTags.PAYMENTS, "Paying for the caller's orders"),
                        tag(ApiTags.ADMIN, "Catalogue, order and user administration; ADMIN role only")));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return new StandardErrorResponses();
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
