package com.darshan.ecommerce.common.exception;

public class InsufficientStockException extends ConflictException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
