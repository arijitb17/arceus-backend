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
 * The one place a request per se becomes "usage" (as opposed to a domain
 * event about something a service did). Only called from ProxyController
 * for requests ApiKeyAuthFilter resolved a project scope for - Portal
 * browser traffic (JWT-authenticated) is navigation, not the kind of
 * "calls against your project" usage a developer or a future billing
 * system cares about. See ArceusEventsTopology.RK_USAGE_REQUEST_RECORDED's
 * javadoc for why this publishes one event per request rather than
 * something heavier: monitoring-service aggregates these into daily
 * rollups (UsageRollup) and this exchange already comfortably carries
 * every other domain event in the platform, so there was no reason to
 * introduce a second transport just for this.
 * <p>
 * Fire-and-forget, same posture as every other publisher in this
 * platform: a publish failure is logged and swallowed, never allowed to
 * fail or slow down the request it's describing.
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
