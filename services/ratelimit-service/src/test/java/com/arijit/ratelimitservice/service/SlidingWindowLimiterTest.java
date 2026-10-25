package com.arijit.ratelimitservice.service;

import com.arijit.ratelimitservice.dto.RateLimitCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SlidingWindowLimiterTest {

    @Mock StringRedisTemplate redis;
    @InjectMocks SlidingWindowLimiter limiter;

    private void scriptReturns(List<Object> result) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
    }

    @Test
    void allowedRequestReportsRemainingQuota() {
        scriptReturns(List.of(1L, 3L));

        RateLimitCheckResponse r = limiter.tryConsume("user:1", 10, 60);

        assertThat(r.allowed()).isTrue();
        assertThat(r.remainingRequests()).isEqualTo(7);
        assertThat(r.retryAfter()).isZero();
    }

    @Test
    void lastAllowedRequestLeavesZeroRemaining() {
        scriptReturns(List.of(1L, 10L));

        assertThat(limiter.tryConsume("k", 10, 60).remainingRequests()).isZero();
    }

    @Test
    void remainingNeverNegative() {
        scriptReturns(List.of(1, 15));

        assertThat(limiter.tryConsume("k", 10, 60).remainingRequests()).isZero();
    }

    @Test
    void deniedRequestComputesRetryFromOldestEntry() {
        long oldest = System.currentTimeMillis() - 20_000;
        scriptReturns(List.of(0L, 10L, String.valueOf((double) oldest)));

        RateLimitCheckResponse r = limiter.tryConsume("k", 10, 60);

        assertThat(r.allowed()).isFalse();
        assertThat(r.remainingRequests()).isZero();
        assertThat(r.retryAfter()).isBetween(38L, 40L);
    }

    @Test
    void deniedWithoutOldestScoreFallsBackToFullWindow() {
        scriptReturns(List.of(0L, 10L));

        assertThat(limiter.tryConsume("k", 10, 45).retryAfter()).isEqualTo(45);
    }

    @Test
    void retryAfterNeverNegativeWhenOldestAlreadyExpired() {
        long ancient = System.currentTimeMillis() - 10 * 60_000;
        scriptReturns(List.of(0L, 10L, String.valueOf((double) ancient)));

        assertThat(limiter.tryConsume("k", 10, 60).retryAfter()).isZero();
    }

    @Test
    void passesNamespacedKeyAndParametersToScript() {
        scriptReturns(List.of(1L, 1L));

        limiter.tryConsume("api:42", 5, 30);

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly("ratelimit:sliding:api:42");
        List<String> a = args.getAllValues();
        assertThat(a.get(1)).isEqualTo("30000");
        assertThat(a.get(2)).isEqualTo("5");
        assertThat(a.get(3)).contains(":").hasSizeGreaterThan(20);
    }
}
