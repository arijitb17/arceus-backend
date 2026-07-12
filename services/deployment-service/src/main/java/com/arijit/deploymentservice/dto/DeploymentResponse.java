package com.arijit.deploymentservice.dto;

import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;

import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        String serviceName,
        String serviceVersion,
        String dockerImage,
        Environment environment,
        DeploymentStatus status,
        String createdBy,
        String approvedBy,
        String rollbackVersion,
        UUID rolledBackFromId,
        String correlationId,
        String releaseNotes,
        boolean requiresApproval,
        String schedulerJobId,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant scheduledAt,
        Instant startedAt,
        Instant completedAt
) {
    public static DeploymentResponse from(Deployment d) {
        return new DeploymentResponse(
                d.getId(),
                d.getServiceName(),
                d.getServiceVersion(),
                d.getDockerImage(),
                d.getEnvironment(),
                d.getStatus(),
                d.getCreatedBy(),
                d.getApprovedBy(),
                d.getRollbackVersion(),
                d.getRolledBackFromId(),
                d.getCorrelationId(),
                d.getReleaseNotes(),
                d.isRequiresApproval(),
                d.getSchedulerJobId(),
                d.getLastError(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getScheduledAt(),
                d.getStartedAt(),
                d.getCompletedAt()
        );
    }
}
