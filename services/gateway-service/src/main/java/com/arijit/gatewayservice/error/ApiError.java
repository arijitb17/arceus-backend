package com.arijit.gatewayservice.error;

import java.time.Instant;

/**
 * The one error shape every Gateway response uses, whether it's thrown
 * from a controller (via GlobalExceptionHandler) or written directly from
 * a servlet Filter (JWT rejection, rate limiting) before the request ever
 * reaches Spring MVC.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId
) {
    public static ApiError of(int status, String error, String message, String path, String requestId) {
        return new ApiError(Instant.now(), status, error, message, path, requestId);
    }
}
