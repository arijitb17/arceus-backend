package com.arijit.ratelimitservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

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
