package com.arijit.notificationservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.notificationservice.entity.NotificationChannel;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * notification-service's "evaluate feature flags before executing
 * configurable functionality" integration point - lets an operator
 * disable an entire channel (e.g. {@code notification.channel.email.enabled})
 * without a redeploy. Checked once per send, in
 * NotificationDispatchService#createAndSend.
 * <p>
 * Fails OPEN on a feature-flag-service outage, same convention as every
 * other auxiliary-service integration on this platform - see
 * auth-service's FeatureFlagGateService for the fuller reasoning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelFlagGateService {

    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${arceus.notification.feature-flags.environment:PRODUCTION}")
    private String environment;

    /** A missing flag, or an unreachable feature-flag-service, means the channel is enabled. */
    public boolean isChannelEnabled(NotificationChannel channel) {
        String flagKey = "notification.channel." + channel.name().toLowerCase() + ".enabled";
        try {
            EvaluateFlagResponse response = featureFlagStub.evaluateFlag(EvaluateFlagRequest.newBuilder()
                    .setKey(flagKey)
                    .setEnvironment(environment)
                    .setDefaultValue("true")
                    .build());
            return !response.getFound() || response.getEnabled();
        } catch (StatusRuntimeException e) {
            log.warn("Could not reach feature-flag-service to evaluate '{}' - allowing the {} channel through: {}",
                    flagKey, channel, e.getStatus());
            return true;
        }
    }
}
