package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;


public record ApiKeyCreatedEvent(
        UUID userId,
        String email,
        UUID apiKeyId,
        String keyPrefix,
        String name,
        Instant occurredAt
) implements DomainEvent {
    public static ApiKeyCreatedEvent now(UUID userId, String email, UUID apiKeyId, String keyPrefix, String name) {
        return new ApiKeyCreatedEvent(userId, email, apiKeyId, keyPrefix, name, Instant.now());
    }

    @Override
    public String eventType() {
        return "API_KEY_CREATED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_API_KEY_CREATED;
    }
}
