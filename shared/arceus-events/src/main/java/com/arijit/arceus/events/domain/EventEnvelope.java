package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * What actually goes over RabbitMQ for every domain event. Wrapping the
 * event in a generic envelope (rather than publishing e.g. UserRegisteredEvent
 * directly) means audit-service can have a single {@code @RabbitListener}
 * that understands every current and future event type without a growing
 * list of typed listener methods - it just stores {@code eventType} +
 * {@code payload} verbatim.
 * <p>
 * {@code eventId} is what makes the platform's audit trail exactly-once:
 * RabbitMQ is at-least-once, so any consumer can see the same envelope
 * more than once after a redelivery. Every consumer must treat
 * {@code eventId} as an idempotency key (e.g. a unique DB constraint, or a
 * Redis SETNX) rather than assuming "one message = one event".
 * <p>
 * {@code correlationId}, {@code userId}, {@code ipAddress}, and
 * {@code userAgent} exist purely so audit-service (the platform's system of
 * record) can populate those fields without ever having to parse them back
 * out of a free-form payload map. Any of them may be {@code null} when the
 * information genuinely isn't available (e.g. a background job has no HTTP
 * request to pull an IP/user-agent from).
 *
 * @param eventId       unique id for this specific event instance - generated
 *                      once by the publisher and never regenerated on retry
 * @param eventType     e.g. "USER_REGISTERED" - see the constant on each event
 * @param occurredAt    when the event happened (not when it was published/consumed)
 * @param sourceService the service that published this event, e.g. "auth-service"
 * @param correlationId ties this event back to the originating HTTP request
 *                      (propagated end-to-end from the Gateway's X-Request-Id)
 * @param userId        the user this event is about/acting as, when known
 * @param ipAddress     caller's IP, when the event was triggered by an HTTP request
 * @param userAgent     caller's User-Agent, when the event was triggered by an HTTP request
 * @param payload       the event's own fields, flattened to a Map so any consumer
 *                      can read it without depending on the exact record shape
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String userId,
        String ipAddress,
        String userAgent,
        Map<String, Object> payload
) {

    /**
     * Convenience factory for publishers: fills in a fresh random
     * {@code eventId} so callers never accidentally reuse one across
     * distinct events.
     */
    public static EventEnvelope of(
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String userId,
            String ipAddress,
            String userAgent,
            Map<String, Object> payload
    ) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                userId,
                ipAddress,
                userAgent,
                payload
        );
    }
}
