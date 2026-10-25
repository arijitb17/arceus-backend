package com.arijit.arceus.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    RateLimitResult tryConsume(String key, int limit, Duration window);
}
