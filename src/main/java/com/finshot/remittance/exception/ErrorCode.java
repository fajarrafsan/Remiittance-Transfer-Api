package com.finshot.remittance.exception;

public enum ErrorCode {
    VALIDATION_ERROR(400),
    NOT_FOUND(404),
    CONFLICT(409),
    LIMIT_EXCEEDED(422);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
