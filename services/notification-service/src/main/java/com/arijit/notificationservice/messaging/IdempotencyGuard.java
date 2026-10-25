package com.arijit.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {
    private static final String KEY_PREFIX = "notification:processed:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.idempotency.ttl-hours:24}")
    private long ttlHours;

    public boolean alreadyProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            log.debug("Idempotency check skipped: message has no id");
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + messageId));
    }

    public void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + messageId, "1", Duration.ofHours(ttlHours));
    }
}
