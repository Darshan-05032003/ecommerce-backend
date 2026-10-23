package com.darshan.ecommerce.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.notifications")
@Validated
public record NotificationProperties(@NotBlank @Email String from) {
}
