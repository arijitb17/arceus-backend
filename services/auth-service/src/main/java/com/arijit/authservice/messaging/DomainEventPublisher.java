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
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return java.util.Optional.ofNullable(servletAttrs.getRequest());
        }
        return java.util.Optional.empty();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
