package com.arijit.arceus.events.domain;

import java.time.Instant;
import java.util.UUID;

public record LoginSuccessfulEvent(
        UUID userId,
        String email,
        Instant occurredAt
) implements DomainEvent {
    public static LoginSuccessfulEvent now(UUID userId, String email) {
        return new LoginSuccessfulEvent(userId, email, Instant.now());
    }

    @Override
    public String eventType() {
        return "LOGIN_SUCCESSFUL";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_LOGIN_SUCCESSFUL;
    }
}
