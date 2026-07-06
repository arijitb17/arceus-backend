package com.arijit.notificationservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * notification-service's queue is only bound to "user.registered" today
 * (see RabbitMQConfig), so this listener only ever sees UserRegisteredEvent
 * envelopes - but it reads defensively by eventType anyway, since RabbitMQ
 * bindings can be widened later without this method needing to change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventConsumer {

    private final NotificationDispatchService dispatchService;

    @RabbitListener(queues = ArceusEventsTopology.NOTIFICATION_QUEUE)
    public void handle(EventEnvelope envelope) {
        try {
            if ("USER_REGISTERED".equals(envelope.eventType())) {
                Map<String, Object> payload = envelope.payload();
                String email = String.valueOf(payload.get("email"));
                String name = String.valueOf(payload.get("name"));
                dispatchService.createAndSend(NotificationChannel.EMAIL, "WELCOME", email, Map.of("name", name));
            } else {
                log.debug("Ignoring event type {} - notification-service has no rule for it yet", envelope.eventType());
            }
        } catch (Exception e) {
            // Best-effort, same as OtpConsumer - no dead-letter queue yet.
            log.error("Failed to handle domain event {}: {}", envelope.eventType(), e.getMessage());
        }
    }
}
