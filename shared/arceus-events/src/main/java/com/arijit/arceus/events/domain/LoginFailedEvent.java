package com.arijit.arceus.events.domain;

import java.time.Instant;

public record LoginFailedEvent(
        String email,
        String reason,
        Instant occurredAt
) implements DomainEvent {

    public static LoginFailedEvent now(String email, String reason) {
        return new LoginFailedEvent(email, reason, Instant.now());
    }

    @Override
    public String eventType() {
        return "LOGIN_FAILED";
    }

    @Override
    public String routingKey() {
        return ArceusEventsTopology.RK_LOGIN_FAILED;
    }
}
