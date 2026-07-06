package com.arijit.arceus.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter backed by a single Redis key per (bucket, identity).
 *
 * Algorithm: INCR the key; if this was the first increment (i.e. the key
 * didn't exist a moment ago), set its TTL to the window length. Both steps
 * happen inside one Lua script so there's no race between two concurrent
 * requests both thinking they're "first" - Redis executes the whole script
 * atomically, single-threaded, same guarantee OtpRedisKeys relies on
 * elsewhere in this platform.
 *
 * Known tradeoff (fixed window, not sliding): a client can burst up to
 * ~2x the limit right at a window boundary (e.g. 5 requests in the last
 * second of one window, 5 more in the first second of the next). For
 * login/OTP/registration abuse protection that's an acceptable tradeoff for
 * the simplicity of one INCR instead of a sorted-set sliding log. If a
 * specific bucket ever needs stricter guarantees, that's the one to swap
 * for a ZSET-based sliding window - the RateLimiter interface doesn't care
 * which strategy sits behind it.
 */
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> INCR_AND_MAYBE_EXPIRE = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    @Override
    public RateLimitResult tryConsume(String key, int limit, Duration window) {
        String redisKey = "ratelimit:" + key;

        Long current = redisTemplate.execute(
                INCR_AND_MAYBE_EXPIRE,
                List.of(redisKey),
                String.valueOf(window.toSeconds())
        );
        long count = current == null ? 0L : current;

        if (count > limit) {
            Long ttl = redisTemplate.getExpire(redisKey);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : window.toSeconds();
            return new RateLimitResult(false, count, retryAfter);
        }
        return new RateLimitResult(true, count, 0);
    }
}
