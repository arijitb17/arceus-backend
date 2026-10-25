package com.arijit.schedulerservice.dto;

import java.time.Instant;

public record UpdateJobRequest(
        String payload,
        Integer priority,
        Integer maxRetries,
        Long retryDelayMs,
        Double backoffMultiplier,
        Boolean exponentialBackoff,
        Instant expiresAt
) {
}
