package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record EmailChangedEvent(
        UUID userId,
        String oldEmail,
        String newEmail,
        Instant occurredAt
) implements DomainEvent {

    public static EmailChangedEvent now(UUID userId, String oldEmail, String newEmail) {
        return new EmailChangedEvent(userId, oldEmail, newEmail, Instant.now());
    }

    @Override
    public String eventType() {
        return "EMAIL_CHANGED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_EMAIL_CHANGED;
    }
}
