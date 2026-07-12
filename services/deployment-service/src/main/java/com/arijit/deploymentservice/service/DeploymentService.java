package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.config.DeploymentProperties;
import com.arijit.deploymentservice.dto.CreateDeploymentRequest;
import com.arijit.deploymentservice.dto.RollbackRequest;
import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import com.arijit.deploymentservice.exception.ApiException;
import com.arijit.deploymentservice.messaging.DeploymentEventPublisher;
import com.arijit.deploymentservice.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The orchestration core of deployment-service: every REST/gRPC entry
 * point delegates here, so both surfaces get identical behavior, identical
 * validation, and publish identical events.
 * <p>
 * Status machine (see {@link DeploymentStatus} for what each value means):
 * <pre>
 *  create()   CREATED  --(environment requires approval)--&gt; PENDING
 *  approve()  PENDING  --------------------------------------&gt; APPROVED
 *  deploy()   CREATED|APPROVED --&gt; DEPLOYING --&gt; RUNNING | FAILED
 *  cancel()   CREATED|PENDING|APPROVED --------------------&gt; CANCELLED
 *  rollback() creates a NEW row (CREATED, approval bypassed by default)
 *             that runs through deploy() like any other deploy; on success
 *             the deployment it targeted is marked ROLLED_BACK.
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private final DeploymentRepository repository;
    private final DeploymentProperties properties;
    private final DeploymentValidationService validationService;
    private final DeploymentHealthVerificationService healthVerificationService;
    private final DeploymentExecutor executor;
    private final DeploymentSchedulingService schedulingService;
    private final FeatureFlagEnablementService featureFlagEnablementService;
    private final VersionService versionService;
    private final DeploymentEventPublisher eventPublisher;

    @Transactional
    public Deployment create(CreateDeploymentRequest request) {
        ValidationResult validation = validationService.validateCreate(request);
        if (!validation.passed()) {
            throw new ApiException("Deployment request failed validation: " + validation.summary(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        boolean requiresApproval = properties.approval().requiredEnvironments().contains(request.environment().name());
        Instant now = Instant.now();

        Deployment deployment = Deployment.builder()
                .serviceName(request.serviceName())
                .serviceVersion(request.serviceVersion())
                .dockerImage(request.dockerImage())
                .environment(request.environment())
                .status(requiresApproval ? DeploymentStatus.PENDING : DeploymentStatus.CREATED)
                .createdBy(request.createdBy())
                .correlationId(request.correlationId())
                .releaseNotes(request.releaseNotes())
                .requiresApproval(requiresApproval)
                .scheduledAt(request.scheduledAt())
                .featureFlagKey(request.featureFlagKey())
                .createdAt(now)
                .updatedAt(now)
                .build();

        deployment = repository.save(deployment);
        eventPublisher.created(deployment);

        if (request.scheduledAt() != null && request.scheduledAt().isAfter(now)) {
            String jobId = schedulingService.scheduleDeploy(deployment);
            if (jobId != null) {
                deployment.setSchedulerJobId(jobId);
                deployment = repository.save(deployment);
            }
        }

        log.info("Created deployment {} for {} {} in {} (status={})",
                deployment.getId(), deployment.getServiceName(), deployment.getServiceVersion(),
                deployment.getEnvironment(), deployment.getStatus());
        return deployment;
    }

    @Transactional
    public Deployment approve(UUID id, String approvedBy) {
        Deployment deployment = getOrThrow(id);
        if (deployment.getStatus() != DeploymentStatus.PENDING) {
            throw new ApiException("Deployment " + id + " is not awaiting approval (status=" + deployment.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }
        deployment.setStatus(DeploymentStatus.APPROVED);
        deployment.setApprovedBy(approvedBy);
        deployment.setUpdatedAt(Instant.now());
        deployment = repository.save(deployment);
        log.info("Deployment {} approved by {}", id, approvedBy);
        return deployment;
    }

    /**
     * Runs the full pipeline synchronously: validation, pre-deploy health
     * check, execution, post-deploy health verification. Called directly
     * by {@code POST /deployments/{id}/deploy}, by the scheduled-deploy
     * callback, and internally by {@link #rollback}.
     */
    @Transactional
    public Deployment deploy(UUID id) {
        Deployment deployment = getOrThrow(id);
        if (deployment.getStatus() != DeploymentStatus.CREATED && deployment.getStatus() != DeploymentStatus.APPROVED) {
            throw new ApiException("Deployment " + id + " cannot be deployed from status " + deployment.getStatus()
                    + (deployment.isRequiresApproval() && deployment.getStatus() == DeploymentStatus.PENDING
                        ? " - it must be approved first" : ""),
                    HttpStatus.CONFLICT);
        }

        ValidationResult validation = validationService.validateForDeploy(deployment);
        if (!validation.passed()) {
            failDeployment(deployment, "Validation failed: " + validation.summary());
            throw new ApiException("Deployment " + id + " failed validation: " + validation.summary(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        ValidationResult preHealth = healthVerificationService.preDeploy(deployment.getServiceName());
        if (!preHealth.passed()) {
            failDeployment(deployment, "Pre-deploy health check failed: " + preHealth.summary());
            throw new ApiException("Deployment " + id + " failed pre-deploy health verification: " + preHealth.summary(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!preHealth.warnings().isEmpty()) {
            log.warn("Pre-deploy health warnings for deployment {}: {}", id, preHealth.summary());
        }

        deployment.setStatus(DeploymentStatus.DEPLOYING);
        deployment.setStartedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        deployment = repository.save(deployment);
        eventPublisher.started(deployment);

        DeploymentExecutor.DeploymentOutcome outcome = executor.execute(deployment);
        ValidationResult postHealth = outcome.success()
                ? healthVerificationService.postDeploy(deployment.getServiceName())
                : new ValidationResult(false, java.util.List.of(), java.util.List.of("execution failed"));

        if (outcome.success() && postHealth.passed()) {
            deployment.setStatus(DeploymentStatus.RUNNING);
            deployment.setCompletedAt(Instant.now());
            deployment.setUpdatedAt(Instant.now());
            deployment.setSchedulerJobId(null);
            deployment = repository.save(deployment);
            eventPublisher.completed(deployment);

            if (deployment.getRolledBackFromId() != null) {
                completeRollbackSupersession(deployment);
            }

            // "Feature Flags enable new version (when applicable)" and
            // "Scheduler executes post-deployment jobs" pipeline steps -
            // both best-effort, both run after the deployment has already
            // committed as RUNNING, so a failure here logs loudly rather
            // than unwinding a deployment that already succeeded.
            featureFlagEnablementService.enableFlagForDeployment(deployment);
            schedulingService.schedulePostDeployVerification(deployment);

            log.info("Deployment {} is now RUNNING", id);
        } else {
            String reason = outcome.success()
                    ? "Post-deploy health verification failed: " + postHealth.summary()
                    : "Execution failed: " + outcome.message();
            failDeployment(deployment, reason);
        }

        return deployment;
    }

    @Transactional
    public Deployment rollback(UUID id, RollbackRequest request) {
        Deployment reference = getOrThrow(id);
        String serviceName = reference.getServiceName();
        Environment environment = reference.getEnvironment();

        Deployment currentRunning = versionService.currentVersion(serviceName, environment)
                .orElseThrow(() -> new ApiException(
                        "No currently running deployment for " + serviceName + " in " + environment + " to roll back",
                        HttpStatus.CONFLICT));

        Deployment target = (request.targetVersion() == null || request.targetVersion().isBlank())
                ? versionService.previousVersion(serviceName, environment)
                    .orElseThrow(() -> new ApiException(
                            "No previous version for " + serviceName + " in " + environment + " to roll back to",
                            HttpStatus.NOT_FOUND))
                : versionService.resolveTargetVersion(serviceName, environment, request.targetVersion());

        if (target.getId().equals(currentRunning.getId())) {
            throw new ApiException("Target version '" + target.getServiceVersion() + "' is already the running version",
                    HttpStatus.CONFLICT);
        }

        boolean requiresApproval = properties.approval().rollbackRequiresApproval()
                && properties.approval().requiredEnvironments().contains(environment.name());
        Instant now = Instant.now();

        Deployment rollbackDeployment = Deployment.builder()
                .serviceName(serviceName)
                .serviceVersion(target.getServiceVersion())
                .dockerImage(target.getDockerImage())
                .environment(environment)
                .status(requiresApproval ? DeploymentStatus.PENDING : DeploymentStatus.CREATED)
                .createdBy(request.initiatedBy() == null || request.initiatedBy().isBlank() ? "unknown" : request.initiatedBy())
                .correlationId(request.correlationId())
                .releaseNotes("Rollback to " + target.getServiceVersion() + " (from " + currentRunning.getServiceVersion() + ")")
                .requiresApproval(requiresApproval)
                .rollbackVersion(target.getServiceVersion())
                .rolledBackFromId(currentRunning.getId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        rollbackDeployment = repository.save(rollbackDeployment);
        eventPublisher.created(rollbackDeployment);
        log.info("Created rollback deployment {} for {} in {}: {} -> {}",
                rollbackDeployment.getId(), serviceName, environment, currentRunning.getServiceVersion(), target.getServiceVersion());

        if (requiresApproval) {
            return rollbackDeployment;
        }
        return deploy(rollbackDeployment.getId());
    }

    @Transactional
    public Deployment cancel(UUID id) {
        Deployment deployment = getOrThrow(id);
        if (deployment.getStatus().isTerminal() || deployment.getStatus() == DeploymentStatus.DEPLOYING
                || deployment.getStatus() == DeploymentStatus.RUNNING) {
            throw new ApiException("Deployment " + id + " cannot be cancelled from status " + deployment.getStatus(),
                    HttpStatus.CONFLICT);
        }
        if (deployment.getSchedulerJobId() != null) {
            schedulingService.cancelScheduled(deployment.getSchedulerJobId());
        }
        deployment.setStatus(DeploymentStatus.CANCELLED);
        deployment.setCompletedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        deployment = repository.save(deployment);
        eventPublisher.cancelled(deployment);
        log.info("Deployment {} cancelled", id);
        return deployment;
    }

    public Deployment getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException("Deployment " + id + " not found", HttpStatus.NOT_FOUND));
    }

    public Page<Deployment> list(String serviceName, Environment environment, DeploymentStatus status, Pageable pageable) {
        boolean hasService = serviceName != null && !serviceName.isBlank();
        boolean hasEnv = environment != null;
        boolean hasStatus = status != null;

        if (hasService && hasEnv && hasStatus) return repository.findByServiceNameAndEnvironmentAndStatus(serviceName, environment, status, pageable);
        if (hasService && hasEnv) return repository.findByServiceNameAndEnvironment(serviceName, environment, pageable);
        if (hasService && hasStatus) return repository.findByServiceNameAndStatus(serviceName, status, pageable);
        if (hasEnv && hasStatus) return repository.findByEnvironmentAndStatus(environment, status, pageable);
        if (hasService) return repository.findByServiceName(serviceName, pageable);
        if (hasEnv) return repository.findByEnvironment(environment, pageable);
        if (hasStatus) return repository.findByStatus(status, pageable);
        return repository.findAll(pageable);
    }

    /** Same filters as {@link #list}, framed as the chronological release history rather than current-state lookup - see class Javadoc on Deployment. */
    public Page<Deployment> history(String serviceName, Environment environment, Pageable pageable) {
        boolean hasService = serviceName != null && !serviceName.isBlank();
        boolean hasEnv = environment != null;

        if (hasService && hasEnv) return repository.findByServiceNameAndEnvironment(serviceName, environment, pageable);
        if (hasService) return repository.findByServiceName(serviceName, pageable);
        if (hasEnv) return repository.findByEnvironment(environment, pageable);
        return repository.findAll(pageable);
    }

    private void failDeployment(Deployment deployment, String reason) {
        deployment.setStatus(DeploymentStatus.FAILED);
        deployment.setCompletedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        deployment.setLastError(reason);
        deployment.setSchedulerJobId(null);
        repository.save(deployment);
        eventPublisher.failed(deployment, reason);
        log.warn("Deployment {} FAILED: {}", deployment.getId(), reason);
    }

    /** Marks the deployment a successful rollback superseded as ROLLED_BACK and publishes deployment.rolledback for it. */
    private void completeRollbackSupersession(Deployment rollbackDeployment) {
        repository.findById(rollbackDeployment.getRolledBackFromId()).ifPresent(superseded -> {
            superseded.setStatus(DeploymentStatus.ROLLED_BACK);
            superseded.setUpdatedAt(Instant.now());
            repository.save(superseded);
            eventPublisher.rolledBack(superseded, rollbackDeployment.getServiceVersion());
            log.info("Deployment {} marked ROLLED_BACK, superseded by {}", superseded.getId(), rollbackDeployment.getId());
        });
    }
}
