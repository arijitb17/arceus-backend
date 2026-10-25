package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String name,
        Instant occurredAt
) implements DomainEvent {
    public static UserRegisteredEvent now(UUID userId, String email, String name) {
        return new UserRegisteredEvent(userId, email, name, Instant.now());
    }

    @Override
    public String eventType() {
        return "USER_REGISTERED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_USER_REGISTERED;
    }
}
