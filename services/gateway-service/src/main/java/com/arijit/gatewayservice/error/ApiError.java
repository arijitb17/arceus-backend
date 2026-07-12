package com.arijit.gatewayservice.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape every Gateway response uses, whether it's thrown
 * from a controller (via GlobalExceptionHandler) or written directly from
 * a servlet Filter (JWT rejection, rate limiting) before the request ever
 * reaches Spring MVC.
 *
 * Deliberately matches the shape every downstream service's own
 * GlobalExceptionHandler already produces ({@code timestamp, status, error,
 * message, correlationId, fields?}), rather than the Gateway's old
 * {@code path, requestId} shape. Since ProxyController.forward() replays a
 * downstream error's raw bytes unchanged, a caller hitting a proxied route
 * was already seeing the downstream shape - the only place callers ever saw
 * the *other* shape was a Gateway-native rejection (401 JWT, 429 rate
 * limit, 503 disabled route, 502 upstream down, 404/405 routing). Matching
 * that shape here means every response a client ever sees, proxied or not,
 * looks the same. "requestId" and "correlationId" were already the same
 * value end-to-end (see RequestIdFilter's javadoc), so this is a rename,
 * not a new concept - and it's still echoed on every response via the
 * X-Request-Id header regardless of this body shape.
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
