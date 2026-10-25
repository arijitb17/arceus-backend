package com.arijit.authservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.authservice.exception.ApiException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagGateService {
    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${app.feature-flags.environment:PRODUCTION}")
    private String environment;

    public void requireEnabled(String flagKey, String actionDescription) {
        try {
            EvaluateFlagResponse response = featureFlagStub.evaluateFlag(EvaluateFlagRequest.newBuilder()
                    .setKey(flagKey)
                    .setEnvironment(environment)
                    .setDefaultValue("true")
                    .build());

            if (response.getFound() && !response.getEnabled()) {
                throw new ApiException(actionDescription + " is temporarily disabled", HttpStatus.SERVICE_UNAVAILABLE);
            }
        } catch (StatusRuntimeException e) {
            log.warn("Could not reach feature-flag-service to evaluate '{}' - allowing {} through: {}",
                    flagKey, actionDescription, e.getStatus());
        }
    }
}
