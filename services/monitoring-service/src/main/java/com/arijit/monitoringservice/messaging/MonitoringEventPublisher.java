package com.arijit.monitoringservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.monitoringservice.entity.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One publish() per platform-observability transition, reusing the exact
 * same {@link EventEnvelope} shape every other Arceus publisher uses (see
 * feature-flag-service's FlagEventPublisher for the same pattern) - this is
 * what lets any downstream consumer bind on {@code "#"} and capture every
 * service-down/alert transition for free, with zero code changes on this
 * end. This is the seam a future notification-dispatch service would
 * subscribe to (Slack/PagerDuty/email) without monitoring-service itself
 * ever needing to know an external integration exists - see this module's
 * pom.xml description.
 * <p>
 * Same failure posture as every other publisher in the platform: a publish
 * failure is logged and swallowed. It must never block or fail the polling
 * cycle that produced it - the persisted snapshot/alert row is the durable
 * record; this is a bonus for external subscribers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void serviceDown(String serviceName, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", serviceName);
        payload.put("errorMessage", errorMessage);
        publish(ArceusEventsTopology.RK_SERVICE_DOWN, "SERVICE_DOWN", payload);
    }

    public void serviceRecovered(String serviceName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", serviceName);
        publish(ArceusEventsTopology.RK_SERVICE_RECOVERED, "SERVICE_RECOVERED", payload);
    }

    public void alertTriggered(AlertEvent event) {
        publish(ArceusEventsTopology.RK_ALERT_TRIGGERED, "ALERT_TRIGGERED", alertPayload(event));
    }

    public void alertResolved(AlertEvent event) {
        publish(ArceusEventsTopology.RK_ALERT_RESOLVED, "ALERT_RESOLVED", alertPayload(event));
    }

    private Map<String, Object> alertPayload(AlertEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertEventId", event.getId() != null ? event.getId().toString() : null);
        payload.put("ruleName", event.getRuleName());
        payload.put("type", event.getType().name());
        payload.put("serviceName", event.getServiceName());
        payload.put("severity", event.getSeverity().name());
        payload.put("message", event.getMessage());
        payload.put("metricValue", event.getMetricValue());
        return payload;
    }

    private void publish(String routingKey, String eventType, Map<String, Object> payload) {
        try {
            EventEnvelope envelope = EventEnvelope.of(
                    eventType,
                    Instant.now(),
                    sourceService,
                    null,
                    null,
                    null,
                    null,
                    payload
            );
            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, routingKey, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish {}: {}", eventType, e.getMessage());
        }
    }
}
