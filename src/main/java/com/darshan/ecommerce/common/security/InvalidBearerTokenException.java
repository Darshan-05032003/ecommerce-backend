package com.darshan.ecommerce.common.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidBearerTokenException extends AuthenticationException {

    public InvalidBearerTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
