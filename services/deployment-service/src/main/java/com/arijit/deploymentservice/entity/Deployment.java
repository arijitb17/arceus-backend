package com.arijit.deploymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per deployment attempt - a fresh create, or a rollback (which is
 * itself just a new deployment row pointed at an earlier version, see
 * {@link #rolledBackFromId}). This row is simultaneously the deployment
 * metadata the platform spec asks for, the unit POST /deployments/{id}/*
 * acts on, and (as a table, queried chronologically) the release history
 * for its serviceName+environment - there's no separate history table,
 * the same way a job's row in scheduler-service is both its current state
 * and, alongside every other job, the audit trail.
 */
@Entity
@Table(name = "deployments", indexes = {
        @Index(name = "idx_deployment_service_env_status", columnList = "serviceName,environment,status"),
        @Index(name = "idx_deployment_service_env_completed", columnList = "serviceName,environment,completedAt"),
        @Index(name = "idx_deployment_correlation_id", columnList = "correlationId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String serviceName;

    @Column(nullable = false, length = 100)
    private String serviceVersion;

    @Column(nullable = false, length = 500)
    private String dockerImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(length = 200)
    private String approvedBy;

    /** Only set on a deployment row created by POST /deployments/{id}/rollback - the version it rolled back to. */
    @Column(length = 100)
    private String rollbackVersion;

    /** Only set on a rollback row - the deployment this one superseded, marked ROLLED_BACK once this row reaches RUNNING. */
    private UUID rolledBackFromId;

    @Column(length = 64)
    private String correlationId;

    @Column(columnDefinition = "TEXT")
    private String releaseNotes;

    /** Computed at creation from DeploymentProperties.Approval.requiredEnvironments - never re-evaluated after creation, even if the config later changes. */
    @Builder.Default
    private boolean requiresApproval = false;

    /** Non-null only while a scheduled deploy (via scheduler-service) is pending - see DeploymentSchedulingService. Cleared once the callback fires. */
    @Column(length = 64)
    private String schedulerJobId;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Non-null only for a deploy scheduled for the future - see DeploymentSchedulingService/DeploymentCallbackController. */
    private Instant scheduledAt;

    private Instant startedAt;

    private Instant completedAt;

    /**
     * Optional - the feature-flag-service key (same environment as this
     * deployment) to enable once this deployment reaches RUNNING. Null
     * means the deployment isn't gated by a flag, so the pipeline's
     * "Feature Flags enable new version (when applicable)" step is a
     * no-op - see service.FeatureFlagEnablementService.
     */
    @Column(length = 200)
    private String featureFlagKey;

    /** Optimistic locking - a concurrent approve/deploy/cancel call, or the scheduled-deploy callback racing a manual deploy, must not silently clobber each other. */
    @Version
    private Long version;
}
