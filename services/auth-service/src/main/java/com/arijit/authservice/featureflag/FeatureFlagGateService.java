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

/**
 * auth-service's "evaluate feature flags before executing configurable
 * functionality" integration point. Currently gates
 * {@link com.arijit.authservice.service.AuthService#register}
 * behind {@code auth.registration.enabled} - a maintenance-mode
 * kill-switch, same shape as the Gateway's per-route flags (see
 * gateway-service's RouteFlagGatewayClient), just enforced at the business
 * action itself rather than at the edge.
 * <p>
 * Fails OPEN on a feature-flag-service outage - same convention as every
 * other auxiliary-service integration on this platform (rate limiting,
 * deployment health checks, the Gateway's own route flags): an outage in
 * a control-plane service must never itself take down a core user-facing
 * action like registration. An operator who explicitly disabled
 * registration and then loses feature-flag-service will need to disable
 * it a different way (e.g. scaling registration's own replica count to
 * zero) until it's back - that's a deliberate, documented tradeoff, not
 * an oversight.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagGateService {

    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${app.feature-flags.environment:PRODUCTION}")
    private String environment;

    /** Throws 503 if the named flag exists and is explicitly disabled. A missing flag, or an unreachable feature-flag-service, means "on". */
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
