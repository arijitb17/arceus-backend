package com.arijit.ratelimitservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Fields are interpreted per algorithm:
 * - SLIDING_WINDOW: limit + windowSeconds (max `limit` calls in any rolling `windowSeconds` window)
 * - TOKEN_BUCKET:   capacity (falls back to limit) + refillPerSecond
 *
 * @param key             fully-namespaced identity being limited, e.g. "login:ip:1.2.3.4"
 * @param algorithm       defaults to SLIDING_WINDOW if omitted
 * @param limit           sliding window: requests allowed per window. Also used as the
 *                        token bucket's capacity if `capacity` isn't set.
 * @param windowSeconds   sliding window: window length in seconds
 * @param capacity        token bucket: max tokens the bucket can hold
 * @param refillPerSecond token bucket: tokens added back per second
 */
public record RateLimitCheckRequest(
        @NotBlank(message = "key is required")
        String key,

        RateLimitAlgorithm algorithm,

        @Positive(message = "limit must be positive")
        Integer limit,

        @Positive(message = "windowSeconds must be positive")
        Integer windowSeconds,

        @Positive(message = "capacity must be positive")
        Integer capacity,

        @Positive(message = "refillPerSecond must be positive")
        Double refillPerSecond
) {
    public RateLimitAlgorithm algorithmOrDefault() {
        return algorithm != null ? algorithm : RateLimitAlgorithm.SLIDING_WINDOW;
    }
}
