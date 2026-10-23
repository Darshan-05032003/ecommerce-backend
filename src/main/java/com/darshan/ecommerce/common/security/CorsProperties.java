package com.darshan.ecommerce.common.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties("app.cors")
@Validated
public record CorsProperties(
        @NotEmpty List<@Pattern(regexp = "https?://[^/\\s?#]+", message = "must be an origin such as https://shop.example.com")
                String> allowedOrigins) {
}
