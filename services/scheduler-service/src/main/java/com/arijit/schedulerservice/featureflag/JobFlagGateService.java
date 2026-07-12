package com.arijit.schedulerservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * scheduler-service's "evaluate feature flags before executing
 * configurable functionality" integration point - lets an operator pause
 * every job belonging to one calling service (e.g.
 * {@code scheduler.jobs.notification-service.enabled}) without cancelling
 * or deleting the jobs themselves. Checked once per claimed job, in
 * JobDispatchService#runClaimedJob, right before handing it to the
 * resolved JobExecutor.
 * <p>
 * Fails OPEN on a feature-flag-service outage, same convention as every
 * other auxiliary-service integration on this platform - see
 * auth-service's FeatureFlagGateService for the fuller reasoning. A
 * suppressed run is treated as a success (not a failure) by the caller,
 * so a recurring job still advances to its next scheduled time instead of
 * retrying immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobFlagGateService {

    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${arceus.scheduler.feature-flags.environment:PRODUCTION}")
    private String environment;

    /** A missing flag, or an unreachable feature-flag-service, means jobs for this service are enabled. */
    public boolean isExecutionEnabledFor(String serviceName) {
        String flagKey = "scheduler.jobs." + serviceName + ".enabled";
        try {
            EvaluateFlagResponse response = featureFlagStub.evaluateFlag(EvaluateFlagRequest.newBuilder()
                    .setKey(flagKey)
                    .setEnvironment(environment)
                    .setBucketKey(serviceName)
                    .setDefaultValue("true")
                    .build());
            return !response.getFound() || response.getEnabled();
        } catch (StatusRuntimeException e) {
            log.warn("Could not reach feature-flag-service to evaluate '{}' - allowing execution through: {}", flagKey, e.getStatus());
            return true;
        }
    }
}
