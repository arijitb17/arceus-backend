package com.arijit.arceus.ratelimit;

public record RateLimitResult(boolean allowed, long currentCount, long retryAfterSeconds) {
}
