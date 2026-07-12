package com.arijit.deploymentservice.grpc;

import com.arijit.deploymentservice.entity.Deployment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Converts the JPA {@link Deployment} entity to {@code
 * com.arijit.arceus.grpc.deployment.Deployment} (the wire message -
 * referenced fully-qualified throughout this class since its simple name
 * collides with the entity). Timestamps go over the wire as epoch millis
 * (0 = "not set"), matching deployment.proto's comments.
 */
@Component
public class DeploymentProtoMapper {

    public com.arijit.arceus.grpc.deployment.Deployment toProto(Deployment deployment) {
        var builder = com.arijit.arceus.grpc.deployment.Deployment.newBuilder()
                .setId(deployment.getId().toString())
                .setServiceName(deployment.getServiceName())
                .setServiceVersion(deployment.getServiceVersion())
                .setDockerImage(deployment.getDockerImage())
                .setEnvironment(deployment.getEnvironment().name())
                .setStatus(deployment.getStatus().name())
                .setCreatedBy(deployment.getCreatedBy())
                .setRequiresApproval(deployment.isRequiresApproval())
                .setCreatedAtEpochMillis(toEpochMillis(deployment.getCreatedAt()))
                .setUpdatedAtEpochMillis(toEpochMillis(deployment.getUpdatedAt()))
                .setScheduledAtEpochMillis(toEpochMillis(deployment.getScheduledAt()))
                .setStartedAtEpochMillis(toEpochMillis(deployment.getStartedAt()))
                .setCompletedAtEpochMillis(toEpochMillis(deployment.getCompletedAt()));

        if (deployment.getApprovedBy() != null) builder.setApprovedBy(deployment.getApprovedBy());
        if (deployment.getRollbackVersion() != null) builder.setRollbackVersion(deployment.getRollbackVersion());
        if (deployment.getRolledBackFromId() != null) builder.setRolledBackFromId(deployment.getRolledBackFromId().toString());
        if (deployment.getCorrelationId() != null) builder.setCorrelationId(deployment.getCorrelationId());
        if (deployment.getReleaseNotes() != null) builder.setReleaseNotes(deployment.getReleaseNotes());
        if (deployment.getSchedulerJobId() != null) builder.setSchedulerJobId(deployment.getSchedulerJobId());
        if (deployment.getLastError() != null) builder.setLastError(deployment.getLastError());
        if (deployment.getFeatureFlagKey() != null) builder.setFeatureFlagKey(deployment.getFeatureFlagKey());

        return builder.build();
    }

    private long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
