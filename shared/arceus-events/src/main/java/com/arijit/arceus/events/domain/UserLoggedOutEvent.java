package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record UserLoggedOutEvent(
        UUID userId,
        String email,
        Instant occurredAt
) implements DomainEvent {
    public static UserLoggedOutEvent now(UUID userId, String email) {
        return new UserLoggedOutEvent(userId, email, Instant.now());
    }

    @Override
    public String eventType() {
        return "USER_LOGGED_OUT";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_USER_LOGGED_OUT;
    }
}
