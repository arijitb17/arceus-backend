package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record PasswordChangedEvent(
        UUID userId,
        String email,
        Instant occurredAt
) implements DomainEvent {

    public static PasswordChangedEvent now(UUID userId, String email) {
        return new PasswordChangedEvent(userId, email, Instant.now());
    }

    @Override
    public String eventType() {
        return "PASSWORD_CHANGED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_PASSWORD_CHANGED;
    }
}
