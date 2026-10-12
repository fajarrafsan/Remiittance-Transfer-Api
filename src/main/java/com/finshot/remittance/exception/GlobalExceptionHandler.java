package com.finshot.remittance.exception;

import com.finshot.remittance.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleException(ApiException e) {
        return error(e.code().httpStatus(), e.code().name(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(400, ErrorCode.VALIDATION_ERROR.name(), "request body is missing or is not valid JSON");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleWrongContentType(HttpMediaTypeNotSupportedException e) {
        return error(400, ErrorCode.VALIDATION_ERROR.name(), "Content-Type must be application/json");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleUnknownPath(NoResourceFoundException e) {
        return error(404, ErrorCode.NOT_FOUND.name(), "No endpoint at /" + e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleWrongMethod(HttpRequestMethodNotSupportedException e) {
        return error(405, "METHOD_NOT_ALLOWED", e.getMessage() + "is not supperted on this path");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnExpected(Exception e) {
        log.error("Unexpected error", e);
        return error(500, "INTERNAL_ERROR", "unexpected error");
    }


    private static ResponseEntity<ApiError> error(int status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(error, message));
    }


}
