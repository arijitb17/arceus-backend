package com.arijit.deploymentservice.messaging;

/**
 * Payload for {@code DeploymentMessagingTopology#APPROVAL_DECISIONS_QUEUE}
 * - the asynchronous alternative to a human calling
 * {@code POST /deployments/{id}/approve} directly (see that queue's
 * Javadoc for why this exists as a separate ingress point).
 *
 * @param deploymentId the deployment this decision applies to
 * @param decision     {@code APPROVE} or {@code REJECT}
 * @param decidedBy    who/what made the decision (a person's email, or a bot's identity)
 * @param reason       optional free-text justification, mostly useful on REJECT
 */
public record ApprovalDecisionMessage(String deploymentId, String decision, String decidedBy, String reason) {
}
