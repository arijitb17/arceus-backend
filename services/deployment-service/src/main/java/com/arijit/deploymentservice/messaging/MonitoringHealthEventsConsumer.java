package com.arijit.deploymentservice.messaging;

import com.arijit.arceus.events.deployment.DeploymentMessagingTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The "Consume Events: Monitoring health events" surface - deployment-service's
 * own queue, bound (see RabbitMQConfig) to monitoring-service's {@code
 * service.down}/{@code service.recovered}/{@code alert.triggered}/{@code
 * alert.resolved} routing keys on the shared platform event bus.
 * <p>
 * Deliberately observational, not corrective: if a service that currently
 * has a RUNNING deployment goes DOWN or trips a CRITICAL alert, that's
 * logged with the deployment id it's currently running so an operator (or
 * a future dashboard) can correlate the incident with a specific release
 * without hunting through two services' logs by hand. This service does
 * NOT auto-rollback in response - automatic remediation/rollout strategies
 * are explicitly out of scope for now (see DeploymentExecutor's Javadoc);
 * a human decides whether {@code POST /deployments/{id}/rollback} is
 * warranted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringHealthEventsConsumer {

    private final DeploymentRepository deploymentRepository;

    @RabbitListener(queues = DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE)
    public void onHealthEvent(EventEnvelope envelope) {
        Map<String, Object> payload = envelope.payload();
        Object serviceNameValue = payload.get("serviceName");
        if (serviceNameValue == null) {
            // Platform-wide alert rules (no specific service) - nothing for a
            // per-service deployment lookup to correlate here.
            return;
        }
        String serviceName = String.valueOf(serviceNameValue);

        deploymentRepository.findFirstByServiceNameAndStatusOrderByCompletedAtDesc(serviceName, DeploymentStatus.RUNNING)
                .ifPresentOrElse(
                        running -> log.warn("[{}] affecting '{}', which is currently running deployment {} (version {}, deployed by {})",
                                envelope.eventType(), serviceName, running.getId(), running.getServiceVersion(), running.getCreatedBy()),
                        () -> log.info("[{}] affecting '{}' - no currently-RUNNING production deployment on record for it",
                                envelope.eventType(), serviceName));
    }
}
