package com.darshan.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

// A service this application depends on failed or did not answer in time. The request itself was
// fine, so this is neither a 4xx nor a plain 500: 502 when the upstream gave a failure answer,
// 504 when it gave no answer.
public class UpstreamServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    private UpstreamServiceException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static UpstreamServiceException badGateway(String errorCode, String message) {
        return new UpstreamServiceException(HttpStatus.BAD_GATEWAY, errorCode, message);
    }

    public static UpstreamServiceException gatewayTimeout(String errorCode, String message) {
        return new UpstreamServiceException(HttpStatus.GATEWAY_TIMEOUT, errorCode, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
