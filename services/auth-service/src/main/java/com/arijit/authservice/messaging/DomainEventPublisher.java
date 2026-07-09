package com.arijit.authservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.CorrelationConstants;
import com.arijit.arceus.events.domain.DomainEvent;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * One publish() for every domain event, regardless of type - the event
 * itself already knows its routing key (see {@link DomainEvent}). Adding a
 * new event type never touches this class.
 * <p>
 * This is also where every event picks up the metadata audit-service needs
 * to actually be a system of record: a fresh {@code eventId} (the
 * idempotency key downstream consumers dedupe on), the correlation id tying
 * it back to the originating HTTP request, which service published it, and -
 * when this is running inside a real HTTP request thread - the caller's IP
 * and User-Agent. Callers of {@link #publish(DomainEvent)} never have to
 * think about any of this themselves.
 * <p>
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

    @Value("${spring.application.name}")
    private String sourceService;

    @SuppressWarnings("unchecked")
    public void publish(DomainEvent event) {
        try {
            Map<String, Object> payload = objectMapper.convertValue(event, Map.class);
            Object userId = payload.get("userId");

            EventEnvelope envelope = EventEnvelope.of(
                    event.eventType(),
                    event.occurredAt(),
                    sourceService,
                    MDC.get(CorrelationConstants.MDC_KEY),
                    userId != null ? userId.toString() : null,
                    currentRequest().map(this::clientIp).orElse(null),
                    currentRequest().map(r -> r.getHeader("User-Agent")).orElse(null),
                    payload
            );

            rabbitTemplate.convertAndSend(
                    ArceusEventsTopology.EVENTS_EXCHANGE,
                    event.routingKey(),
                    envelope
            );
        } catch (Exception e) {
            log.warn("Failed to publish domain event {}: {}", event.eventType(), e.getMessage());
        }
    }

    private java.util.Optional<HttpServletRequest> currentRequest() {
        // Not every event is published inside an HTTP request thread (a
        // scheduled job could publish one too), so this is deliberately an
        // Optional rather than assuming RequestContextHolder always has
        // something to give back.
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return java.util.Optional.ofNullable(servletAttrs.getRequest());
        }
        return java.util.Optional.empty();
    }

    private String clientIp(HttpServletRequest request) {
        // The Gateway (or any reverse proxy in front of it) sets
        // X-Forwarded-For; fall back to the raw socket address for direct
        // calls (e.g. local dev, or the OAuth2 redirect flow which bypasses
        // the Gateway entirely).
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
