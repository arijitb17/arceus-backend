package com.arijit.arceus.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;


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
