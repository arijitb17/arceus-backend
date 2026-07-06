package com.arijit.authservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.DomainEvent;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * One publish() for every domain event, regardless of type - the event
 * itself already knows its routing key (see {@link DomainEvent}). Adding a
 * new event type never touches this class.
 *
 * Publishing failures are logged and swallowed rather than propagated: a
 * register/login/etc call that already succeeded against the database
 * shouldn't fail the HTTP response just because the audit/notification
 * side-effect couldn't be queued. Auth-service's own behavior (JWTs,
 * password hashing, OTP verification) never depends on this succeeding.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public void publish(DomainEvent event) {
        try {
            Map<String, Object> payload = objectMapper.convertValue(event, Map.class);
            EventEnvelope envelope = new EventEnvelope(event.eventType(), event.occurredAt(), payload);
            rabbitTemplate.convertAndSend(
                    ArceusEventsTopology.EVENTS_EXCHANGE,
                    event.routingKey(),
                    envelope
            );
        } catch (Exception e) {
            log.warn("Failed to publish domain event {}: {}", event.eventType(), e.getMessage());
        }
    }
}
