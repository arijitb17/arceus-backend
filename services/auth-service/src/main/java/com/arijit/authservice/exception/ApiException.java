package com.arijit.authservice.exception;

import org.springframework.http.HttpStatus;

// A generic "something went wrong, here's the HTTP status to return" exception.
// Lets service-layer code throw a business error without knowing/caring about
// how it eventually gets serialized into JSON - GlobalExceptionHandler does that.
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
