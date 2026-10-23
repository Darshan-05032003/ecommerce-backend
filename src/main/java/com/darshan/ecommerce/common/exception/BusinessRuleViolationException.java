package com.darshan.ecommerce.common.exception;

// A well-formed request that breaks a business rule (422). The error code is stable and meant
// for clients to branch on; the message is for people and may change.
public class BusinessRuleViolationException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
