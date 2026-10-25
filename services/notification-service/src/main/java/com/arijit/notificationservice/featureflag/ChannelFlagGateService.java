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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelFlagGateService {
    private final FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub;

    @Value("${arceus.notification.feature-flags.environment:PRODUCTION}")
    private String environment;

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
