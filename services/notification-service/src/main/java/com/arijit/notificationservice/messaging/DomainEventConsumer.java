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

/**
 * notification-service's queue is bound to "user.registered" plus the
 * terminal deployment-lifecycle routing keys (see RabbitMQConfig) - this
 * listener branches on eventType defensively either way, since RabbitMQ
 * bindings can be widened further later without this method needing to
 * change shape.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventConsumer {

    private final NotificationDispatchService dispatchService;
    private final IdempotencyGuard idempotencyGuard;

    // Deployment status notifications aren't about any one end-user, so
    // there's no "recipient" on the event itself the way there is for
    // UserRegisteredEvent - they go to a configured deploy-alerts mailbox
    // instead. Same shape as deployment-service's own
    // arceus.deployment.internal.api-token: a single operator-configured
    // value, not something end users ever touch.
    @Value("${arceus.notification.deployment-alert-email:deploy-alerts@arceus.local}")
    private String deploymentAlertEmail;

    @RabbitListener(queues = ArceusEventsTopology.NOTIFICATION_QUEUE)
    public void handle(EventEnvelope envelope) {
        if (idempotencyGuard.alreadyProcessed(envelope.eventId())) {
            log.info("Skipping redelivered event {} (eventId={}) - already handled",
                    envelope.eventType(), envelope.eventId());
            return;
        }

        // Deliberately NOT catching a broad Exception here anymore - see
        // OtpConsumer's javadoc for why: a transient failure should retry
        // with backoff and dead-letter once exhausted, not be silently
        // logged and dropped.
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

    /** Shared by every deployment.* outcome - same payload shape published by DeploymentEventPublisher, just a different template per outcome. */
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
