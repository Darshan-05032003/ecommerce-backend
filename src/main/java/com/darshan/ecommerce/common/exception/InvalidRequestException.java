package com.darshan.ecommerce.common.exception;

// A request that is malformed in a way Bean Validation cannot express, for example a missing or
// badly formed header (400). Like business rule failures it carries a stable error code.
public class InvalidRequestException extends RuntimeException {

    private final String errorCode;

    public InvalidRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
