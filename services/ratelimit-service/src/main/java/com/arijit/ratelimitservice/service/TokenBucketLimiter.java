package com.arijit.ratelimitservice.service;

import com.arijit.ratelimitservice.dto.RateLimitCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenBucketLimiter {
    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refillPerSecond = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])

            local data = redis.call('HMGET', key, 'tokens', 'lastRefillMillis')
            local tokens = tonumber(data[1])
            local lastRefill = tonumber(data[2])

            if tokens == nil then
                tokens = capacity
                lastRefill = now
            end

            local elapsedSeconds = math.max(0, (now - lastRefill) / 1000.0)
            tokens = math.min(capacity, tokens + elapsedSeconds * refillPerSecond)

            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefillMillis', tostring(now))
            redis.call('EXPIRE', key, ttlSeconds)

            local retryAfter = 0
            if allowed == 0 and refillPerSecond > 0 then
                retryAfter = math.ceil((1 - tokens) / refillPerSecond)
            end

            return {allowed, tostring(tokens), retryAfter}
            """, List.class);

    public RateLimitCheckResponse tryConsume(String key, int capacity, double refillPerSecond) {
        String redisKey = "ratelimit:bucket:" + key;
        long now = System.currentTimeMillis();
        long ttlSeconds = refillPerSecond > 0 ? Math.max(60, (long) (2 * capacity / refillPerSecond)) : 3600;

        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                SCRIPT,
                List.of(redisKey),
                String.valueOf(now), String.valueOf(capacity), String.valueOf(refillPerSecond), String.valueOf(ttlSeconds)
        );

        long allowedFlag = ((Number) result.get(0)).longValue();
        double remainingTokens = Double.parseDouble(String.valueOf(result.get(1)));
        long retryAfter = ((Number) result.get(2)).longValue();

        return new RateLimitCheckResponse(allowedFlag == 1, (long) Math.floor(remainingTokens), retryAfter);
    }
}
