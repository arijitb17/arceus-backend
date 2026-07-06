package com.arijit.arceus.events.domain;

import java.time.Instant;

/**
 * Every domain event knows its own type name and routing key, so the
 * publisher never needs a switch statement mapping classes to strings -
 * {@code publisher.publish(event)} is always enough.
 */
public interface DomainEvent {

    /** Stable, upper-snake-case identifier stored verbatim in the audit log. */
    String eventType();

    /** Routing key used to publish this event onto {@link ArceusEventsTopology#EVENTS_EXCHANGE}. */
    String routingKey();

    Instant occurredAt();
}
