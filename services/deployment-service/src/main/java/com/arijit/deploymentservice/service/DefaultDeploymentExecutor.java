package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.entity.Deployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Today's only {@link DeploymentExecutor} - there is no Kubernetes/Helm/
 * Docker Swarm/GitOps controller wired up yet (explicitly out of scope for
 * this service), so "executing" a deployment means recording that it
 * happened rather than actually rolling out a new container anywhere.
 * This is intentionally the entire orchestration layer for now: real
 * container/process lifecycle management is exactly the seam
 * {@link DeploymentExecutor} exists to let a future implementation fill in
 * without any change to DeploymentService, the REST/gRPC contracts, or
 * anything that calls them.
 */
@Component
@Slf4j
public class DefaultDeploymentExecutor implements DeploymentExecutor {

    @Override
    public DeploymentOutcome execute(Deployment deployment) {
        log.info("Executing deployment {} - {} {} to {} ({}) [no orchestrator wired up yet, recording as deployed]",
                deployment.getId(), deployment.getServiceName(), deployment.getServiceVersion(),
                deployment.getEnvironment(), deployment.getDockerImage());
        return DeploymentOutcome.success("Recorded as deployed (no orchestrator integration yet - see DeploymentExecutor)");
    }
}
