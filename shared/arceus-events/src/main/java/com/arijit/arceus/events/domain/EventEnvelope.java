package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


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
