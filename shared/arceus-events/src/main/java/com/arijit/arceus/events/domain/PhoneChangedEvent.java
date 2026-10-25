package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record PhoneChangedEvent(
        UUID userId,
        String oldPhoneNumber,
        String newPhoneNumber,
        Instant occurredAt
) implements DomainEvent {
    public static PhoneChangedEvent now(UUID userId, String oldPhoneNumber, String newPhoneNumber) {
        return new PhoneChangedEvent(userId, oldPhoneNumber, newPhoneNumber, Instant.now());
    }

    @Override
    public String eventType() {
        return "PHONE_CHANGED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_PHONE_CHANGED;
    }
}
