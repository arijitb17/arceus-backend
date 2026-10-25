package com.arijit.featureflagservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.featureflagservice.entity.FeatureFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The flag's own state change (already committed to Postgres by the time this is called) must never
 * be rolled back just because this best-effort notification couldn't be sent - FlagAuditService's
 * local audit row is the durable record; this is a bonus for external subscribers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlagEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void created(FeatureFlag flag, String actor) {
        publish(ArceusEventsTopology.RK_FLAG_CREATED, "FEATURE_FLAG_CREATED", flag, actor);
    }

    public void updated(FeatureFlag flag, String actor) {
        publish(ArceusEventsTopology.RK_FLAG_UPDATED, "FEATURE_FLAG_UPDATED", flag, actor);
    }

    public void enabled(FeatureFlag flag, String actor) {
        publish(ArceusEventsTopology.RK_FLAG_ENABLED, "FEATURE_FLAG_ENABLED", flag, actor);
    }

    public void disabled(FeatureFlag flag, String actor) {
        publish(ArceusEventsTopology.RK_FLAG_DISABLED, "FEATURE_FLAG_DISABLED", flag, actor);
    }

    public void deleted(FeatureFlag flag, String actor) {
        publish(ArceusEventsTopology.RK_FLAG_DELETED, "FEATURE_FLAG_DELETED", flag, actor);
    }

    private void publish(String routingKey, String eventType, FeatureFlag flag, String actor) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("flagId", flag.getId() != null ? flag.getId().toString() : null);
            payload.put("flagKey", flag.getFlagKey());
            payload.put("environment", flag.getEnvironment().name());
            payload.put("type", flag.getType().name());
            payload.put("status", flag.getStatus().name());
            payload.put("rolloutPercentage", flag.getRolloutPercentage());

            EventEnvelope envelope = EventEnvelope.of(
                    eventType,
                    Instant.now(),
                    sourceService,
                    null,
                    actor,
                    null,
                    null,
                    payload
            );

            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, routingKey, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish {} for flag {}: {}", eventType, flag.getFlagKey(), e.getMessage());
        }
    }
}
