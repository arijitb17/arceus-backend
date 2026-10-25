package com.arijit.featureflagservice;

import com.arijit.featureflagservice.config.FeatureFlagProperties;
import com.arijit.featureflagservice.entity.*;

import java.time.Instant;
import java.util.UUID;

public final class FlagTestData {

    private FlagTestData() {}

    public static FeatureFlag booleanFlag(String key, Environment env, FlagStatus status, boolean value) {
        return FeatureFlag.builder()
                .id(UUID.randomUUID()).flagKey(key).name(key).type(FlagType.BOOLEAN)
                .environment(env).targetType(env == Environment.GLOBAL ? TargetType.GLOBAL : TargetType.ENVIRONMENT)
                .valueBoolean(value).status(status).rolloutPercentage(100)
                .createdBy("tester").updatedBy("tester")
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(3L)
                .build();
    }

    public static FeatureFlagProperties properties(boolean cacheEnabled, long ttl, boolean schedulingEnabled) {
        return new FeatureFlagProperties(
                new FeatureFlagProperties.Cache(cacheEnabled, ttl),
                new FeatureFlagProperties.Internal("internal-token"),
                new FeatureFlagProperties.Scheduling(schedulingEnabled, "http://ff:8087"),
                new FeatureFlagProperties.GrpcClients("sched-token"));
    }
}
