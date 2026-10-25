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
class TokenBucketLimiterTest {

    @Mock StringRedisTemplate redis;
    @InjectMocks TokenBucketLimiter limiter;

    private void scriptReturns(List<Object> result) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
    }

    @Test
    void allowedFloorsFractionalTokens() {
        scriptReturns(List.of(1L, "4.75", 0L));

        RateLimitCheckResponse r = limiter.tryConsume("k", 10, 2.0);

        assertThat(r.allowed()).isTrue();
        assertThat(r.remainingRequests()).isEqualTo(4);
        assertThat(r.retryAfter()).isZero();
    }

    @Test
    void deniedCarriesRetryAfter() {
        scriptReturns(List.of(0L, "0.25", 3L));

        RateLimitCheckResponse r = limiter.tryConsume("k", 10, 0.5);

        assertThat(r.allowed()).isFalse();
        assertThat(r.remainingRequests()).isZero();
        assertThat(r.retryAfter()).isEqualTo(3);
    }

    @Test
    void ttlIsAtLeastSixtySecondsAndScalesWithRefillTime() {
        scriptReturns(List.of(1L, "1", 0L));

        limiter.tryConsume("fast", 10, 100.0);
        limiter.tryConsume("slow", 100, 0.5);

        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).execute(any(RedisScript.class), anyList(),
                args.capture(), args.capture(), args.capture(), args.capture());
        List<String> all = args.getAllValues();
        assertThat(all.get(3)).isEqualTo("60");
        assertThat(all.get(7)).isEqualTo("400");
    }

    @Test
    void zeroRefillUsesOneHourTtl() {
        scriptReturns(List.of(1L, "1", 0L));

        limiter.tryConsume("k", 5, 0);

        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(any(RedisScript.class), anyList(), args.capture(), args.capture(), args.capture(), args.capture());
        assertThat(args.getAllValues().get(3)).isEqualTo("3600");
    }

    @Test
    void usesBucketKeyPrefix() {
        scriptReturns(List.of(1L, "1", 0L));

        limiter.tryConsume("user:9", 5, 1.0);

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), anyString(), anyString(), anyString(), anyString());
        assertThat(keys.getValue()).containsExactly("ratelimit:bucket:user:9");
    }
}
