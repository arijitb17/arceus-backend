package com.arijit.deploymentservice.service;

import com.arijit.arceus.grpc.scheduler.JobIdRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import com.arijit.deploymentservice.config.DeploymentProperties;
import com.arijit.deploymentservice.entity.Deployment;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * The platform spec's "Consume Events: Scheduler deployment jobs" surface:
 * a deployment created with a future {@code scheduledAt} registers an
 * HTTP_CALLBACK job with scheduler-service instead of this service running
 * its own delay/cron loop, exactly the same delegation feature-flag-service
 * uses for scheduled flag flips (see FlagSchedulingService). When the job
 * fires, scheduler-service POSTs to
 * {@code /internal/deployments/{id}/scheduled-deploy} (see
 * DeploymentCallbackController), which triggers the normal
 * DeploymentService#deploy path - a scheduled deploy runs through the
 * exact same validation/execution/health-verification pipeline as a
 * manual one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentSchedulingService {

    private final DeploymentProperties properties;
    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;

    /** Registers the delayed callback for a deployment's {@code scheduledAt}; returns the scheduler-service job id, or {@code null} if scheduling is disabled or failed (deployment still gets created either way - it just waits for a manual POST /deploy instead). */
    public String scheduleDeploy(Deployment deployment) {
        if (!properties.scheduling().enabled()) {
            log.warn("Scheduling disabled (arceus.deployment.scheduling.enabled=false) - deployment {} was created with a "
                    + "scheduledAt but will need a manual POST /deploy", deployment.getId());
            return null;
        }
        long delayMs = Math.max(0, Duration.between(Instant.now(), deployment.getScheduledAt()).toMillis());
        String callbackUrl = properties.scheduling().callbackBaseUrl() + "/internal/deployments/"
                + deployment.getId() + "/scheduled-deploy?token=" + properties.internal().apiToken();

        try {
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName("deploy-" + deployment.getServiceName() + "-" + deployment.getId())
                    .setServiceName("deployment-service")
                    .setTriggerType("DELAY")
                    .setDelayMs(delayMs)
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPayload("{}")
                    .setMaxRetries(0)
                    .setCorrelationId(deployment.getCorrelationId() == null ? "" : deployment.getCorrelationId())
                    .build());
            log.info("Scheduled deployment {} to fire via scheduler-service job {}", deployment.getId(), response.getJob().getId());
            return response.getJob().getId();
        } catch (StatusRuntimeException e) {
            log.error("Failed to schedule deployment {} with scheduler-service: {}", deployment.getId(), e.getStatus());
            return null;
        }
    }

    /** Called when a deployment is cancelled before its scheduled time arrives. */
    public void cancelScheduled(String schedulerJobId) {
        if (schedulerJobId == null || schedulerJobId.isBlank()) {
            return;
        }
        try {
            schedulerStub.cancelJob(JobIdRequest.newBuilder().setId(schedulerJobId).build());
        } catch (StatusRuntimeException e) {
            log.warn("Failed to cancel scheduler-service job {}: {}", schedulerJobId, e.getStatus());
        }
    }

    /**
     * The pipeline's "Scheduler executes post-deployment jobs" step -
     * registers a one-shot HTTP_CALLBACK job that hits
     * {@code /internal/deployments/{id}/post-deploy-verify} a short delay
     * after this deployment reaches RUNNING (see DeploymentCallbackController),
     * giving monitoring-service's health data time to reflect the new
     * process having actually settled in, past its own startup grace period
     * - which the synchronous postDeploy check in {@link DeploymentService#deploy}
     * (run immediately after execution) is often too early to observe.
     * Best-effort like {@link #scheduleDeploy}: a failure here doesn't fail
     * the deployment, which has already succeeded by this point.
     */
    public void schedulePostDeployVerification(Deployment deployment) {
        if (!properties.scheduling().enabled()) {
            return;
        }
        String callbackUrl = properties.scheduling().callbackBaseUrl() + "/internal/deployments/"
                + deployment.getId() + "/post-deploy-verify?token=" + properties.internal().apiToken();

        try {
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName("post-deploy-verify-" + deployment.getServiceName() + "-" + deployment.getId())
                    .setServiceName("deployment-service")
                    .setTriggerType("DELAY")
                    .setDelayMs(properties.scheduling().postDeployVerificationDelayMs())
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPayload("{}")
                    .setMaxRetries(0)
                    .setCorrelationId(deployment.getCorrelationId() == null ? "" : deployment.getCorrelationId())
                    .build());
            log.info("Scheduled post-deploy verification for deployment {} via scheduler-service job {}",
                    deployment.getId(), response.getJob().getId());
        } catch (StatusRuntimeException e) {
            log.warn("Failed to schedule post-deploy verification for deployment {} with scheduler-service: {}",
                    deployment.getId(), e.getStatus());
        }
    }
}
