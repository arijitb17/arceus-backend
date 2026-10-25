package com.arijit.arceus.events.domain;

import java.time.Instant;


public interface DomainEvent {
    String eventType();

    String routingKey();

    Instant occurredAt();
}
