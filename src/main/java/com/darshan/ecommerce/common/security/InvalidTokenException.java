package com.darshan.ecommerce.common.security;

import org.springframework.security.core.AuthenticationException;

// Thrown for a bearer or refresh token that is malformed, expired, revoked or unknown.
// The message is fixed text written by us, so it is safe to return to the client.
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
