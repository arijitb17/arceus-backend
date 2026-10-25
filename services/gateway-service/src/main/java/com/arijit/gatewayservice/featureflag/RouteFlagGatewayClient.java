package com.arijit.gatewayservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deliberately fails OPEN (treats "allowed" as the default) on any feature-flag-service error, same
 * reasoning as RateLimitGatewayClient: an outage in an auxiliary control-plane service must never
 * itself take down every route through the Gateway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RouteFlagGatewayClient {
    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${arceus.gateway.feature-flags.environment:PRODUCTION}")
    private String environment;

    public boolean isRouteEnabled(String routeName) {
        String flagKey = "gateway.route." + routeName + ".enabled";
        try {
            EvaluateFlagResponse response = featureFlagStub.evaluateFlag(EvaluateFlagRequest.newBuilder()
                    .setKey(flagKey)
                    .setEnvironment(environment)
                    .setBucketKey(routeName)
                    .setDefaultValue("true")
                    .build());
            // Not found = no operator has ever configured a kill-switch for this route, which must
            // mean "on", not "off".
            return !response.getFound() || response.getEnabled();
        } catch (StatusRuntimeException e) {
            log.warn("Could not reach feature-flag-service to evaluate '{}' - allowing the request through: {}", flagKey, e.getStatus());
            return true;
        }
    }
}
