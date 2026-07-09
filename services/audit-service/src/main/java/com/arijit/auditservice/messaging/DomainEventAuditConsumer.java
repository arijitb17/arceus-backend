package com.arijit.auditservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.auditservice.entity.AuditLog;
import com.arijit.auditservice.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * One listener for every event type - the whole point of the generic
 * EventEnvelope wire format is that this class never has to grow a new
 * method when a new DomainEvent subtype is added upstream.
 * <p>
 * RabbitMQ is at-least-once, so this method WILL occasionally see the same
 * envelope twice after a redelivery. {@code eventId}'s unique constraint on
 * {@code audit_logs} is what turns that into effectively-once storage: a
 * duplicate insert throws {@link DataIntegrityViolationException}, which is
 * caught and treated as "already recorded, nothing to do" rather than an
 * error.
 * <p>
 * Any OTHER failure (DB connectivity blip, unexpected payload shape) is
 * deliberately NOT swallowed here - it propagates so Spring's listener
 * retry policy (see application.yml's spring.rabbitmq.listener.simple.retry)
 * can retry with backoff, and after retries are exhausted the message is
 * routed to the dead-letter queue instead of being silently lost. That's
 * what "audit failures never break business workflows, but also never
 * silently lose events" actually requires - the two swallow-and-log-only
 * used to satisfy the first half at the cost of the second.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventAuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = ArceusEventsTopology.AUDIT_QUEUE)
    public void handle(EventEnvelope envelope) throws Exception {
        Map<String, Object> payload = envelope.payload();
        String actorEmail = firstNonNull(payload.get("email"), payload.get("newEmail"), payload.get("oldEmail"));

        AuditLog log = AuditLog.builder()
                .eventId(envelope.eventId())
                .eventType(envelope.eventType())
                .sourceService(envelope.sourceService())
                .correlationId(envelope.correlationId())
                .userId(envelope.userId())
                .actorEmail(actorEmail)
                .ipAddress(envelope.ipAddress())
                .userAgent(envelope.userAgent())
                .description(describe(envelope.eventType(), payload))
                .metadataJson(objectMapper.writeValueAsString(payload))
                .occurredAt(envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now())
                .receivedAt(Instant.now())
                .build();

        try {
            auditLogRepository.save(log);
        } catch (DataIntegrityViolationException duplicate) {
            DomainEventAuditConsumer.log.info(
                    "Duplicate delivery of event {} (eventId={}) - already recorded, skipping",
                    envelope.eventType(), envelope.eventId());
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
