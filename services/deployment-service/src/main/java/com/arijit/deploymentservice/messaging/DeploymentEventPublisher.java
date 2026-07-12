package com.arijit.deploymentservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.deploymentservice.entity.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One publish() per deployment lifecycle transition, same
 * {@link EventEnvelope} shape and same failure posture as scheduler-service's
 * JobLifecycleEventPublisher: a publish failure is logged and swallowed,
 * never allowed to roll back or retry the deployment state change that's
 * already committed to Postgres by the time this is called. This is also
 * how "Audit every deployment action" is satisfied without any
 * deployment-service-specific audit logic - audit-service's existing
 * catch-all binding on {@code arceus.events.exchange} picks these up for
 * free, same as it does for every other service's domain events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void created(Deployment deployment) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_CREATED, "DEPLOYMENT_CREATED", deployment, null);
    }

    public void started(Deployment deployment) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_STARTED, "DEPLOYMENT_STARTED", deployment, null);
    }

    public void completed(Deployment deployment) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_COMPLETED, "DEPLOYMENT_COMPLETED", deployment, null);
    }

    public void failed(Deployment deployment, String error) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_FAILED, "DEPLOYMENT_FAILED", deployment, error);
    }

    public void rolledBack(Deployment supersededDeployment, String restoredVersion) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_ROLLED_BACK, "DEPLOYMENT_ROLLED_BACK", supersededDeployment, restoredVersion);
    }

    public void cancelled(Deployment deployment) {
        publish(ArceusEventsTopology.RK_DEPLOYMENT_CANCELLED, "DEPLOYMENT_CANCELLED", deployment, null);
    }

    private void publish(String routingKey, String eventType, Deployment deployment, String note) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deploymentId", deployment.getId().toString());
            payload.put("serviceName", deployment.getServiceName());
            payload.put("serviceVersion", deployment.getServiceVersion());
            payload.put("dockerImage", deployment.getDockerImage());
            payload.put("environment", deployment.getEnvironment().name());
            payload.put("status", deployment.getStatus().name());
            payload.put("createdBy", deployment.getCreatedBy());
            if (deployment.getApprovedBy() != null) {
                payload.put("approvedBy", deployment.getApprovedBy());
            }
            if ("DEPLOYMENT_ROLLED_BACK".equals(eventType)) {
                payload.put("restoredVersion", note);
            } else if (note != null) {
                payload.put("error", note);
            }

            EventEnvelope envelope = EventEnvelope.of(
                    eventType,
                    Instant.now(),
                    sourceService,
                    deployment.getCorrelationId(),
                    null,
                    null,
                    null,
                    payload
            );

            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, routingKey, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish {} for deployment {}: {}", eventType, deployment.getId(), e.getMessage());
        }
    }
}
