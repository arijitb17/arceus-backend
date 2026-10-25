package com.arijit.ratelimitservice.service;

import com.arijit.ratelimitservice.dto.RateLimitCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SlidingWindowLimiter {
    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - windowMillis)
            local count = redis.call('ZCARD', key)

            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, windowMillis)
                return {1, count + 1}
            else
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                return {0, count, oldest[2]}
            end
            """, List.class);

    public RateLimitCheckResponse tryConsume(String key, int limit, int windowSeconds) {
        String redisKey = "ratelimit:sliding:" + key;
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        String member = now + ":" + java.util.UUID.randomUUID();

        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                SCRIPT,
                List.of(redisKey),
                String.valueOf(now), String.valueOf(windowMillis), String.valueOf(limit), member
        );

        long allowedFlag = ((Number) result.get(0)).longValue();
        long count = ((Number) result.get(1)).longValue();

        if (allowedFlag == 1) {
            return new RateLimitCheckResponse(true, Math.max(0, limit - count), 0);
        }

        long retryAfterSeconds = windowSeconds;
        if (result.size() > 2 && result.get(2) != null) {
            long oldestScore = (long) Double.parseDouble(String.valueOf(result.get(2)));
            long msUntilOldestExpires = (oldestScore + windowMillis) - now;
            retryAfterSeconds = Math.max(0, msUntilOldestExpires / 1000);
        }
        return new RateLimitCheckResponse(false, 0, retryAfterSeconds);
    }
}
