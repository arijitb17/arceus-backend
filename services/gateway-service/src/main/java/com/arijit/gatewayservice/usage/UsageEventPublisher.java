package com.arijit.gatewayservice.usage;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fire-and-forget, same posture as every other publisher in this platform: a publish failure is
 * logged and swallowed, never allowed to fail or slow down the request it's describing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void recordRequest(String organizationId, String projectId, String environment, String route, int statusCode) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("organizationId", organizationId);
            payload.put("projectId", projectId);
            payload.put("environment", environment);
            payload.put("route", route);
            payload.put("statusCode", statusCode);

            EventEnvelope envelope = EventEnvelope.of(
                    "USAGE_REQUEST_RECORDED", Instant.now(), sourceService, null, null, null, null, payload);
            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, ArceusEventsTopology.RK_USAGE_REQUEST_RECORDED, envelope);
        } catch (Exception e) {
            log.debug("Failed to publish usage event: {}", e.getMessage());
        }
    }
}
