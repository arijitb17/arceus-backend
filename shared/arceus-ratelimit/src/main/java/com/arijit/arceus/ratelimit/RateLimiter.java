package com.arijit.arceus.ratelimit;

import java.time.Duration;

/**
 * The one thing every rate-limiting rule ultimately needs: "has this key
 * already used up its budget for this window?" Everything else in this
 * module (the annotation, the aspect, the IP resolution) is just convenient
 * plumbing on top of this single method.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one unit of the budget for {@code key}.
     *
     * @param key    fully-resolved, already-namespaced key (callers should
     *               NOT reuse the same key across unrelated rules - see
     *               {@link RateLimited#bucket()} for how the aspect avoids that)
     * @param limit  max allowed calls per window
     * @param window how long each window lasts
     */
    RateLimitResult tryConsume(String key, int limit, Duration window);
}
