package com.arijit.arceus.ratelimit;

/**
 * Thrown by {@link RateLimitAspect} when a call exceeds its budget. Deliberately
 * has no HTTP-status/JSON-body opinion baked in - that's each service's job
 * (auth-service maps it to 429 in its GlobalExceptionHandler, same place
 * ApiException is handled) so this module stays usable by non-web callers too.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
