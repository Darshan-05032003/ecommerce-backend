package com.darshan.ecommerce.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String type = "Bearer";
    
    public AuthResponseDTO(String token) {
        this.token = token;
    }
}
