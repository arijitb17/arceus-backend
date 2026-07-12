package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.entity.Deployment;

/**
 * The one seam that would change if/when this platform grows a real
 * orchestrator underneath deployment-service (Kubernetes, Docker Swarm,
 * a GitOps controller, blue-green/canary rollout logic) - deliberately
 * NOT implemented yet, per this service's scope. DeploymentService always
 * calls this interface, never a concrete orchestrator, so swapping
 * {@link DefaultDeploymentExecutor} for e.g. a future
 * {@code KubernetesDeploymentExecutor} changes zero public REST/gRPC
 * contracts and zero callers - only which Spring bean satisfies this
 * interface. Same pattern as scheduler-service's JobExecutor interface
 * (HttpCallbackJobExecutor/MessagePublishJobExecutor today, anything else
 * later).
 */
public interface DeploymentExecutor {

    /**
     * Actually perform the deployment described by {@code deployment}
     * (already validated and past pre-deploy health checks by the time
     * this is called). Must never throw for an ordinary deploy failure -
     * report it via {@link DeploymentOutcome#failure(String)} instead, so
     * DeploymentService can persist the failure and publish
     * deployment.failed cleanly. Only an unexpected/programmer error
     * should propagate as an exception.
     */
    DeploymentOutcome execute(Deployment deployment);

    record DeploymentOutcome(boolean success, String message) {
        public static DeploymentOutcome success(String message) {
            return new DeploymentOutcome(true, message);
        }

        public static DeploymentOutcome failure(String message) {
            return new DeploymentOutcome(false, message);
        }
    }
}
