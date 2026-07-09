package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

/** Published when a user (or an admin) revokes an API key. */
public record ApiKeyRevokedEvent(
        UUID userId,
        String email,
        UUID apiKeyId,
        Instant occurredAt
) implements DomainEvent {

    public static ApiKeyRevokedEvent now(UUID userId, String email, UUID apiKeyId) {
        return new ApiKeyRevokedEvent(userId, email, apiKeyId, Instant.now());
    }

    @Override
    public String eventType() {
        return "API_KEY_REVOKED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_API_KEY_REVOKED;
    }
}
