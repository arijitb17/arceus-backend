package com.arijit.auditservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.auditservice.entity.AuditLog;
import com.arijit.auditservice.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * One listener for every event type - the whole point of the generic
 * EventEnvelope wire format is that this class never has to grow a new
 * method when a new DomainEvent subtype is added upstream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventAuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = ArceusEventsTopology.AUDIT_QUEUE)
    public void handle(EventEnvelope envelope) {
        try {
            Map<String, Object> payload = envelope.payload();
            String actorEmail = firstNonNull(payload.get("email"), payload.get("newEmail"), payload.get("oldEmail"));

            auditLogRepository.save(AuditLog.builder()
                    .eventType(envelope.eventType())
                    .actorEmail(actorEmail)
                    .description(describe(envelope.eventType(), payload))
                    .metadataJson(objectMapper.writeValueAsString(payload))
                    .occurredAt(envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now())
                    .receivedAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist audit log for event {}: {}", envelope.eventType(), e.getMessage());
        }
    }

    private String describe(String eventType, Map<String, Object> payload) {
        Object email = payload.get("email");
        return switch (eventType) {
            case "USER_REGISTERED" -> "User " + email + " registered";
            case "LOGIN_SUCCESSFUL" -> "User " + email + " logged in successfully";
            case "LOGIN_FAILED" -> "Failed login attempt for " + email + " (" + payload.get("reason") + ")";
            case "PASSWORD_CHANGED" -> "User " + email + " changed their password";
            case "EMAIL_CHANGED" -> "User changed email from " + payload.get("oldEmail") + " to " + payload.get("newEmail");
            case "PHONE_CHANGED" -> "User changed phone number";
            case "USER_LOGGED_OUT" -> "User " + email + " logged out";
            default -> eventType + " event received";
        };
    }

    @SafeVarargs
    private String firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
}
