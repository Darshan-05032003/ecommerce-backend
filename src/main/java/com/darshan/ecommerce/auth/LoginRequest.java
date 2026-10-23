package com.darshan.ecommerce.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@Schema(example = "jane.doe@example.com") @NotBlank @Email String email,
                           @Schema(example = "Str0ng!pass") @NotBlank String password) {
}
