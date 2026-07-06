package com.arijit.ratelimitservice.dto;

public record RateLimitCheckResponse(
        boolean allowed,
        long remainingRequests,
        long retryAfter
) {
}
