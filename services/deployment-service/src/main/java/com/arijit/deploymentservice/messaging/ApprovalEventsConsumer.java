package com.arijit.deploymentservice.messaging;

import com.arijit.arceus.events.deployment.DeploymentMessagingTopology;
import com.arijit.deploymentservice.exception.ApiException;
import com.arijit.deploymentservice.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@link ApprovalDecisionMessage}s - the "Consume Events:
 * Approval events" surface. Applies the exact same
 * DeploymentService#approve/#cancel path {@code POST /deployments/{id}/approve}
 * and {@code POST /deployments/{id}/cancel} use, so an async approval
 * decision behaves identically to a human clicking the equivalent REST
 * action. A decision that can't be applied (unknown id, deployment no
 * longer PENDING) is logged and dropped rather than rethrown - unlike a
 * scheduler retry request, a stale approval decision will never become
 * valid on redelivery, so there's nothing a DLQ retry would fix.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalEventsConsumer {

    private final DeploymentService deploymentService;

    @RabbitListener(queues = DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE)
    public void onApprovalDecision(ApprovalDecisionMessage message) {
        UUID deploymentId;
        try {
            deploymentId = UUID.fromString(message.deploymentId());
        } catch (IllegalArgumentException e) {
            log.error("Malformed approval decision, deploymentId is not a UUID: {}", message.deploymentId());
            return;
        }

        try {
            if ("APPROVE".equalsIgnoreCase(message.decision())) {
                deploymentService.approve(deploymentId, message.decidedBy());
                log.info("Applied async approval for deployment {} by {}", deploymentId, message.decidedBy());
            } else if ("REJECT".equalsIgnoreCase(message.decision())) {
                deploymentService.cancel(deploymentId);
                log.info("Applied async rejection (cancellation) for deployment {} by {}: {}",
                        deploymentId, message.decidedBy(), message.reason());
            } else {
                log.warn("Unknown approval decision '{}' for deployment {}", message.decision(), deploymentId);
            }
        } catch (ApiException e) {
            log.info("Approval decision for deployment {} was a no-op: {}", deploymentId, e.getMessage());
        }
    }
}
