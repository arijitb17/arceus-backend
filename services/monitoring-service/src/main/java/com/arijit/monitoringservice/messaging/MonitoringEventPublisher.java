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
