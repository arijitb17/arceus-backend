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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetTokenService(redis);
        ReflectionTestUtils.setField(service, "expirationMinutes", 10L);
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void generateTokenStoresEmailUnderTokenKey() {
        String token = service.generateToken("a@b.com");

        assertThat(UUID.fromString(token)).isNotNull();
        verify(ops).set("reset-token:" + token, "a@b.com", Duration.ofMinutes(10));
    }

    @Test
    void consumeReturnsEmailAndDeletesToken() {
        when(ops.get("reset-token:t1")).thenReturn("a@b.com");

        assertThat(service.consumeToken("t1")).contains("a@b.com");

        verify(redis).delete("reset-token:t1");
    }

    @Test
    void consumeUnknownTokenReturnsEmptyWithoutDelete() {
        when(ops.get("reset-token:nope")).thenReturn(null);

        assertThat(service.consumeToken("nope")).isEmpty();

        verify(redis, never()).delete(anyString());
    }
}
