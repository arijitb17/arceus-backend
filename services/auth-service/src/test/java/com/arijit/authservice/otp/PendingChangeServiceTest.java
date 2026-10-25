package com.arijit.authservice.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PendingChangeServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    private PendingChangeService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PendingChangeService(redis);
        ReflectionTestUtils.setField(service, "expirationMinutes", 7L);
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void storesPendingEmailWithTtl() {
        service.storePendingEmail(userId, "n@x.y");
        verify(ops).set("pending-email-change:" + userId, "n@x.y", Duration.ofMinutes(7));
    }

    @Test
    void storesPendingPhoneWithTtl() {
        service.storePendingPhone(userId, "+911234567890");
        verify(ops).set("pending-phone-change:" + userId, "+911234567890", Duration.ofMinutes(7));
    }

    @Test
    void consumePendingEmailIsSingleUse() {
        when(ops.get("pending-email-change:" + userId)).thenReturn("n@x.y");

        assertThat(service.consumePendingEmail(userId)).contains("n@x.y");

        verify(redis).delete("pending-email-change:" + userId);
    }

    @Test
    void consumePendingPhoneIsSingleUse() {
        when(ops.get("pending-phone-change:" + userId)).thenReturn("+911234567890");

        assertThat(service.consumePendingPhone(userId)).contains("+911234567890");

        verify(redis).delete("pending-phone-change:" + userId);
    }

    @Test
    void consumeMissingReturnsEmpty() {
        when(ops.get(any())).thenReturn(null);

        assertThat(service.consumePendingEmail(userId)).isEmpty();
        assertThat(service.consumePendingPhone(userId)).isEmpty();
        verify(redis, never()).delete(any(String.class));
    }
}
