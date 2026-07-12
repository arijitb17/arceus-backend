package com.arijit.deploymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Binds the {@code arceus.deployment.*} tree in application.yml.
 */
@ConfigurationProperties(prefix = "arceus.deployment")
public record DeploymentProperties(
        Approval approval,
        Validation validation,
        Scheduling scheduling,
        Internal internal,
        GrpcClients grpcClients
) {

    /**
     * Which environments gate a deployment behind POST /deployments/{id}/approve
     * before it can be deployed - see DeploymentService#create. Rollbacks
     * bypass this by default ({@code rollbackRequiresApproval=false}):
     * a rollback is an emergency mitigation action, and forcing it through
     * the same approval queue as a forward deploy would defeat its purpose
     * as a fast path back to a known-good version. Set true for
     * environments where an approved rollback is still mandatory policy.
     */
    public record Approval(
            List<String> requiredEnvironments,
            boolean rollbackRequiresApproval
    ) {
    }

    /** DeploymentValidationService's pre-deploy checks - see that class's Javadoc for what each one verifies. */
    public record Validation(
            boolean strict,
            String versionPattern,
            String dockerImagePattern,
            Map<String, List<String>> requiredEnvVars,
            Map<String, List<String>> dependencies
    ) {
    }

    /** Delegates scheduled deploys to scheduler-service rather than polling for due deployments itself - see DeploymentSchedulingService. */
    public record Scheduling(
            boolean enabled,
            String callbackBaseUrl,
            /** Delay before scheduler-service fires the post-deploy confirmation job - see DeploymentSchedulingService#schedulePostDeployVerification. */
            long postDeployVerificationDelayMs
    ) {
    }

    /** Shared-secret gRPC/internal auth - see grpc.GrpcAuthInterceptor / security.InternalTokenAuthFilter. */
    public record Internal(
            String apiToken
    ) {
    }

    /**
     * The x-internal-token each downstream service's own GrpcAuthInterceptor
     * expects - one token per target, since monitoring-service,
     * scheduler-service, and feature-flag-service each have their own
     * independent {@code arceus.<service>.internal.api-token} secret (not
     * a platform-wide shared value). See config.GrpcClientConfig for where
     * these get attached to every outbound stub.
     */
    public record GrpcClients(
            String monitoringToken,
            String schedulerToken,
            String featureFlagToken
    ) {
    }
}
