package com.darshan.ecommerce.common.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.seed")
@Profile("dev")
@Validated
public record SeedProperties(@NotBlank @Email String adminEmail,
                             @NotBlank @Size(min = 8) String adminPassword) {
}
