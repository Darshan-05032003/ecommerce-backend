package com.darshan.ecommerce.user;

import com.darshan.ecommerce.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Email @Size(max = 120) String email,
        // BCrypt only hashes the first 72 bytes, so longer passwords would be silently truncated
        @NotBlank @Size(max = 72) @StrongPassword String password,
        @NotBlank @Size(max = 100) String fullName) {
}
