package com.arijit.notificationservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventConsumer {
    private final NotificationDispatchService dispatchService;
    private final IdempotencyGuard idempotencyGuard;

    @Value("${arceus.notification.deployment-alert-email:deploy-alerts@arceus.local}")
    private String deploymentAlertEmail;

    @RabbitListener(queues = ArceusEventsTopology.NOTIFICATION_QUEUE)
    public void handle(EventEnvelope envelope) {
        if (idempotencyGuard.alreadyProcessed(envelope.eventId())) {
            log.info("Skipping redelivered event {} (eventId={}) - already handled",
                    envelope.eventType(), envelope.eventId());
            return;
        }

        // Deliberately NOT catching a broad Exception here anymore - see OtpConsumer's javadoc for
        // why: a transient failure should retry with backoff and dead-letter once exhausted, not be
        // silently logged and dropped.
        switch (envelope.eventType()) {
            case "USER_REGISTERED" -> {
                Map<String, Object> payload = envelope.payload();
                String email = String.valueOf(payload.get("email"));
                String name = String.valueOf(payload.get("name"));
                dispatchService.createAndSend(NotificationChannel.EMAIL, "WELCOME", email, Map.of("name", name));
            }
            case "DEPLOYMENT_COMPLETED" -> sendDeploymentNotification("DEPLOYMENT_SUCCESS", envelope);
            case "DEPLOYMENT_FAILED" -> sendDeploymentNotification("DEPLOYMENT_FAILURE", envelope);
            case "DEPLOYMENT_ROLLED_BACK" -> sendDeploymentNotification("DEPLOYMENT_ROLLED_BACK", envelope);
            case "DEPLOYMENT_CANCELLED" -> sendDeploymentNotification("DEPLOYMENT_CANCELLED", envelope);
            default -> log.debug("Ignoring event type {} - notification-service has no rule for it yet", envelope.eventType());
        }

        idempotencyGuard.markProcessed(envelope.eventId());
    }

    private void sendDeploymentNotification(String templateCode, EventEnvelope envelope) {
        Map<String, Object> payload = envelope.payload();
        Map<String, Object> variables = new HashMap<>();
        variables.put("deploymentId", payload.get("deploymentId"));
        variables.put("serviceName", payload.get("serviceName"));
        variables.put("serviceVersion", payload.get("serviceVersion"));
        variables.put("environment", payload.get("environment"));
        variables.put("status", payload.get("status"));
        variables.put("createdBy", payload.getOrDefault("createdBy", "unknown"));
        variables.put("error", payload.getOrDefault("error", ""));
        variables.put("restoredVersion", payload.getOrDefault("restoredVersion", ""));
        dispatchService.createAndSend(NotificationChannel.EMAIL, templateCode, deploymentAlertEmail, variables);
    }
}
