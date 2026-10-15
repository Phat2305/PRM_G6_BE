package com.example.art_course.common;

import org.springframework.http.HttpStatus;

public class AppError extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public AppError(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public AppError(int statusCode, String errorCode, String message) {
        this(HttpStatus.valueOf(statusCode), errorCode, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
