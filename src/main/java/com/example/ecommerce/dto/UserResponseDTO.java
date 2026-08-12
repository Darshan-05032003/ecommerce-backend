package com.example.ecommerce.dto;

import lombok.Data;
import java.time.Instant;
import java.util.Set;

@Data
public class UserResponseDTO {
    private Long id;
    private String email;
    private String fullName;
    private Set<String> roles;
    private Instant createdAt;
}
