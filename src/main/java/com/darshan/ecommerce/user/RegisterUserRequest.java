package com.darshan.ecommerce.user;

import com.darshan.ecommerce.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @Schema(example = "jane.doe@example.com") @NotBlank @Email @Size(max = 120) String email,
        // BCrypt only hashes the first 72 bytes, so longer passwords would be silently truncated
        @Schema(example = "Str0ng!pass") @NotBlank @Size(max = 72) @StrongPassword String password,
        @Schema(example = "Jane Doe") @NotBlank @Size(max = 100) String fullName) {
}
