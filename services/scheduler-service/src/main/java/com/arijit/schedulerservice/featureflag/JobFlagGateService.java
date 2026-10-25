package com.arijit.schedulerservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobFlagGateService {
    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${arceus.scheduler.feature-flags.environment:PRODUCTION}")
    private String environment;

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
