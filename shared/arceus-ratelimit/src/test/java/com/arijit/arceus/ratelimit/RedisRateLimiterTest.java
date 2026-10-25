package com.arijit.arceus.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisRateLimiter(redisTemplate);
    }

    private void stubIncrement(Long value) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(value);
    }

    @Test
    void firstRequestIsAllowed() {
        stubIncrement(1L);

        RateLimitResult result = limiter.tryConsume("login:ip:1.1.1.1", 5, Duration.ofSeconds(60));

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(1);
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    void requestAtExactLimitIsAllowed() {
        stubIncrement(5L);

        RateLimitResult result = limiter.tryConsume("k", 5, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(5);
    }

    @Test
    void requestOverLimitIsRejectedWithRemainingTtl() {
        stubIncrement(6L);
        when(redisTemplate.getExpire("ratelimit:k")).thenReturn(42L);

        RateLimitResult result = limiter.tryConsume("k", 5, Duration.ofSeconds(60));

        assertThat(result.allowed()).isFalse();
        assertThat(result.currentCount()).isEqualTo(6);
        assertThat(result.retryAfterSeconds()).isEqualTo(42);
    }

    @Test
    void fallsBackToWindowWhenTtlIsNull() {
        stubIncrement(10L);
        when(redisTemplate.getExpire("ratelimit:k")).thenReturn(null);

        RateLimitResult result = limiter.tryConsume("k", 5, Duration.ofSeconds(90));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(90);
    }

    @Test
    void fallsBackToWindowWhenKeyHasNoExpiry() {
        stubIncrement(10L);
        when(redisTemplate.getExpire("ratelimit:k")).thenReturn(-1L);

        assertThat(limiter.tryConsume("k", 5, Duration.ofSeconds(30)).retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void nullScriptResultIsTreatedAsZeroAndAllowed() {
        stubIncrement(null);

        RateLimitResult result = limiter.tryConsume("k", 1, Duration.ofSeconds(10));

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isZero();
    }

    @Test
    void prefixesKeyAndPassesWindowSecondsToScript() {
        stubIncrement(1L);

        limiter.tryConsume("bucket:abc", 3, Duration.ofMinutes(2));

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arg.capture());
        assertThat(keys.getValue()).containsExactly("ratelimit:bucket:abc");
        assertThat(arg.getValue()).isEqualTo("120");
    }
}
