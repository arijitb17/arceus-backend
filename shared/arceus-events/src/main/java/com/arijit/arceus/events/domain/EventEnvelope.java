package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.Map;

/**
 * What actually goes over RabbitMQ for every domain event. Wrapping the
 * event in a generic envelope (rather than publishing e.g. UserRegisteredEvent
 * directly) means audit-service can have a single {@code @RabbitListener}
 * that understands every current and future event type without a growing
 * list of typed listener methods - it just stores {@code eventType} +
 * {@code payload} verbatim.
 *
 * @param eventType   e.g. "USER_REGISTERED" - see the constant on each event
 * @param occurredAt  when the event happened (not when it was published/consumed)
 * @param payload     the event's fields, flattened to a Map so any consumer
 *                    can read it without depending on the exact record shape
 */
public record EventEnvelope(
        String eventType,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
