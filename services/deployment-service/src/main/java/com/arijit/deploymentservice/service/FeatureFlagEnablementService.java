package com.arijit.deploymentservice.service;

import com.arijit.arceus.grpc.featureflag.EnableFlagRequest;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.arceus.grpc.featureflag.FlagActionResponse;
import com.arijit.deploymentservice.entity.Deployment;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The deployment pipeline's "Feature Flags enable new version (when
 * applicable)" step. Only fires when the deployment carries a
 * {@link Deployment#getFeatureFlagKey()} - most deployments don't gate
 * anything behind a flag, so this is a no-op for them.
 * <p>
 * Deliberately best-effort, same reasoning as
 * {@link DeploymentHealthVerificationService}'s preDeploy check: a
 * feature-flag-service outage shouldn't unwind an otherwise-successful
 * deployment that already reached RUNNING. Failure here is logged loudly
 * and left for an operator to enable the flag manually, not retried
 * automatically - retrying flag enablement blind could race an operator
 * who already handled it by hand after seeing the warning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagEnablementService {

    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagGrpcStub;

    public void enableFlagForDeployment(Deployment deployment) {
        String flagKey = deployment.getFeatureFlagKey();
        if (flagKey == null || flagKey.isBlank()) {
            return;
        }

        try {
            FlagActionResponse response = featureFlagGrpcStub.enableFlag(EnableFlagRequest.newBuilder()
                    .setKey(flagKey)
                    .setEnvironment(deployment.getEnvironment().name())
                    .setActor("deployment-service:" + deployment.getId())
                    .build());

            if (response.getSuccess()) {
                log.info("Enabled flag '{}' in {} for deployment {}", flagKey, deployment.getEnvironment(), deployment.getId());
            } else {
                log.warn("feature-flag-service declined to enable flag '{}' in {} for deployment {}: {}",
                        flagKey, deployment.getEnvironment(), deployment.getId(), response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.warn("Could not reach feature-flag-service to enable flag '{}' in {} for deployment {} - it will need to be enabled manually: {}",
                    flagKey, deployment.getEnvironment(), deployment.getId(), e.getStatus());
        }
    }
}
