package com.arijit.featureflagservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code arceus.feature-flag.*} tree in application.yml.
 */
@ConfigurationProperties(prefix = "arceus.feature-flag")
public record FeatureFlagProperties(
        Cache cache,
        Internal internal,
        Scheduling scheduling
) {

    /** Redis-backed evaluation cache - see FlagCacheService. */
    public record Cache(
            boolean enabled,
            long ttlSeconds
    ) {
    }

    /** Shared-secret REST/gRPC auth for internal callers - see security.InternalTokenAuthFilter / grpc.GrpcAuthInterceptor. */
    public record Internal(
            String apiToken
    ) {
    }

    /** How scheduled activation/deactivation delegates to scheduler-service - see service.FlagSchedulingService. */
    public record Scheduling(
            boolean enabled,
            /** Base URL scheduler-service's HTTP_CALLBACK jobs use to call back into this service. */
            String callbackBaseUrl
    ) {
    }
}
