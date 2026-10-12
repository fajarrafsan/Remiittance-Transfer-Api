package com.finshot.remittance.exception;

public class ApiException extends RuntimeException{
    private final ErrorCode code;

    public ApiException(String message, ErrorCode code) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException validation(String message) {
        return new ApiException(message, ErrorCode.VALIDATION_ERROR);
    }

    public static ApiException notFound(String message) {
        return new ApiException(message, ErrorCode.NOT_FOUND);
    }

    public static ApiException conflict(String message) {
        return new ApiException(message, ErrorCode.CONFLICT);
    }

    public static ApiException limitExceeded(String message) {
        return new ApiException(message, ErrorCode.LIMIT_EXCEEDED);
    }
}
