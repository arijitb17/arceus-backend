package com.arijit.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RabbitMQ is at-least-once: any consumer here can see the same message
 * twice after a redelivery (crash between processing and ack, a
 * connection blip, etc). Without this, a redelivered OTP message would
 * send the same email/SMS twice, and a redelivered domain event would
 * create two "welcome" notifications for one registration.
 * <p>
 * Deliberately two separate steps rather than one atomic
 * "claim-then-process": {@link #alreadyProcessed} only checks, and
 * {@link #markProcessed} is called by the listener AFTER a send actually
 * succeeds. If a message is claimed as "processed" before it's actually
 * sent and the send then throws (transient SMTP/SMS failure), the retry
 * Spring schedules next would see it as already-done and wrongly skip
 * it - the exact failure mode this class exists to prevent, just moved to
 * a different trigger.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final String KEY_PREFIX = "notification:processed:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.idempotency.ttl-hours:24}")
    private long ttlHours;

    public boolean alreadyProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            // No id to dedupe on - fail open rather than silently dropping
            // every message without one (e.g. from an older publisher
            // version that predates messageId/eventId).
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
