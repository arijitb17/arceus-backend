package com.arijit.gatewayservice.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Deliberately matches the shape every downstream service's own GlobalExceptionHandler already
 * produces ({@code timestamp, status, error, message, correlationId, fields?}), rather than the
 * Gateway's old {@code path, requestId} shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String correlationId,
        Map<String, String> fields
) {
    public static ApiError of(int status, String error, String message, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, correlationId, null);
    }

    public static ApiError of(int status, String error, String message, String correlationId, Map<String, String> fields) {
        return new ApiError(Instant.now(), status, error, message, correlationId, fields);
    }
}
