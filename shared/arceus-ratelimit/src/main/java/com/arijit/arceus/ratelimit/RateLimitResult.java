package com.arijit.arceus.ratelimit;

/**
 * @param allowed         true if this call is within budget and should proceed
 * @param currentCount    how many calls this key has used in the current window (for logging/metrics)
 * @param retryAfterSeconds if not allowed, how long until the window resets - lets callers
 *                          return a proper {@code Retry-After} header instead of a bare 429
 */
public record RateLimitResult(boolean allowed, long currentCount, long retryAfterSeconds) {
}
