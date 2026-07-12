package com.arijit.deploymentservice.dto;

import com.arijit.deploymentservice.entity.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * POST /deployments body (and the shape CreateDeploymentRequest on the
 * gRPC side mirrors field-for-field). {@code scheduledAt} is optional - if
 * present, the deployment is created as usual (still subject to approval
 * gating) but not deployed until that instant, via a scheduler-service
 * HTTP_CALLBACK job (see DeploymentSchedulingService); if absent, the
 * caller triggers the actual deploy explicitly via POST /deployments/{id}/deploy.
 */
public record CreateDeploymentRequest(
        @NotBlank String serviceName,
        @NotBlank String serviceVersion,
        @NotBlank String dockerImage,
        @NotNull Environment environment,
        @NotBlank String createdBy,
        String releaseNotes,
        String correlationId,
        Instant scheduledAt,
        /** Optional - feature-flag-service key (same environment) to enable once this deployment reaches RUNNING. */
        String featureFlagKey
) {
}
