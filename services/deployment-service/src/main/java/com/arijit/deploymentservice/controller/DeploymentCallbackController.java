package com.arijit.deploymentservice.controller;

import com.arijit.deploymentservice.config.DeploymentProperties;
import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.exception.ApiException;
import com.arijit.deploymentservice.service.DeploymentHealthVerificationService;
import com.arijit.deploymentservice.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The receiving end of {@link com.arijit.deploymentservice.service.DeploymentSchedulingService}
 * - scheduler-service's HTTP_CALLBACK executor POSTs here at the instant a
 * deployment's {@code scheduledAt} arrives. Left open at the Spring
 * Security filter-chain level (see SecurityConfig) and instead checks the
 * {@code token} query parameter itself, same reasoning as feature-flag-service's
 * FlagScheduleCallbackController: scheduler-service's callback executor
 * can't attach a custom auth header per job, only a URL it POSTs to.
 * <p>
 * If approval is still pending when the scheduled time arrives, this
 * deliberately does NOT deploy - it leaves the deployment as PENDING and
 * records why, so a human still has to approve before anything ships to
 * an environment that requires it. The deployment can then be deployed
 * manually via {@code POST /deployments/{id}/deploy} as soon as it's
 * approved.
 */
@RestController
@RequestMapping("/internal/deployments")
@RequiredArgsConstructor
@Slf4j
public class DeploymentCallbackController {

    private final DeploymentService deploymentService;
    private final DeploymentProperties properties;
    private final DeploymentHealthVerificationService healthVerificationService;

    @PostMapping("/{id}/scheduled-deploy")
    public void scheduledDeploy(@PathVariable UUID id, @RequestParam String token) {
        requireValidToken(token);

        Deployment deployment = deploymentService.getOrThrow(id);
        if (deployment.getStatus() == DeploymentStatus.PENDING) {
            log.warn("scheduler-service callback: deployment {} is still awaiting approval - not deploying yet", id);
            return;
        }
        if (deployment.getStatus() != DeploymentStatus.CREATED && deployment.getStatus() != DeploymentStatus.APPROVED) {
            log.info("scheduler-service callback: deployment {} is no longer eligible to deploy (status={}) - skipping",
                    id, deployment.getStatus());
            return;
        }

        log.info("scheduler-service callback: deploying scheduled deployment {}", id);
        deploymentService.deploy(id);
    }

    /**
     * The "Monitoring confirms deployment success" pipeline step -
     * scheduler-service's HTTP_CALLBACK executor POSTs here once the delay
     * registered by DeploymentSchedulingService#schedulePostDeployVerification
     * elapses. Re-checks health the same way the synchronous postDeploy
     * check inside DeploymentService#deploy does, just later, after the new
     * process has had time to fully settle. Doesn't flip the deployment's
     * status - RUNNING already happened - this is a confirmation/alerting
     * step, so an unhealthy result is logged loudly rather than silently
     * discarded, for an operator (or a future alert integration) to act on.
     */
    @PostMapping("/{id}/post-deploy-verify")
    public void postDeployVerify(@PathVariable UUID id, @RequestParam String token) {
        requireValidToken(token);

        Deployment deployment = deploymentService.getOrThrow(id);
        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            log.info("scheduler-service callback: deployment {} is no longer RUNNING (status={}) - skipping post-deploy verification",
                    id, deployment.getStatus());
            return;
        }

        var result = healthVerificationService.postDeploy(deployment.getServiceName());
        if (result.passed()) {
            log.info("Post-deploy verification confirms deployment {} ({} {} in {}) is healthy",
                    id, deployment.getServiceName(), deployment.getServiceVersion(), deployment.getEnvironment());
        } else {
            log.warn("Post-deploy verification found deployment {} ({} {} in {}) UNHEALTHY after settling: {}",
                    id, deployment.getServiceName(), deployment.getServiceVersion(), deployment.getEnvironment(), result.summary());
        }
    }

    private void requireValidToken(String token) {
        if (token == null || !token.equals(properties.internal().apiToken())) {
            throw new ApiException("Invalid internal callback token", HttpStatus.UNAUTHORIZED);
        }
    }
}
