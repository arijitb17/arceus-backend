package com.arijit.authservice.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ExchangeServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    private OAuth2ExchangeService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2ExchangeService(redis);
        ReflectionTestUtils.setField(service, "expirationSeconds", 60L);
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void issueCodeStoresUserIdWithShortTtl() {
        UUID userId = UUID.randomUUID();

        String code = service.issueCode(userId);

        assertThat(UUID.fromString(code)).isNotNull();
        verify(ops).set("oauth2:exchange:" + code, userId.toString(), Duration.ofSeconds(60));
    }

    @Test
    void consumeCodeReturnsUserIdAndDeletes() {
        UUID userId = UUID.randomUUID();
        when(ops.get("oauth2:exchange:c")).thenReturn(userId.toString());

        assertThat(service.consumeCode("c")).contains(userId);

        verify(redis).delete("oauth2:exchange:c");
    }

    @Test
    void consumeUnknownCodeIsEmpty() {
        when(ops.get("oauth2:exchange:x")).thenReturn(null);

        assertThat(service.consumeCode("x")).isEmpty();

        verify(redis, never()).delete(any(String.class));
    }
}
