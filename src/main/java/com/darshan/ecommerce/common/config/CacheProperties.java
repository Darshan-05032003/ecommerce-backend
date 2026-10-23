package com.darshan.ecommerce.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("app.cache")
@Validated
public record CacheProperties(@NotNull @Valid Ttl ttl) {

    public record Ttl(@NotNull Duration products, @NotNull Duration categories) {

        public Ttl {
            requirePositive(products, "app.cache.ttl.products");
            requirePositive(categories, "app.cache.ttl.categories");
        }

        private static void requirePositive(Duration value, String name) {
            if (value != null && (value.isZero() || value.isNegative())) {
                throw new IllegalArgumentException(name + " must be a positive duration");
            }
        }
    }
}
