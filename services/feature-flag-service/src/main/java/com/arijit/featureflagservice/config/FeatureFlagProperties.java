package com.arijit.featureflagservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code arceus.feature-flag.*} tree in application.yml.
 */
@ConfigurationProperties(prefix = "arceus.feature-flag")
public record FeatureFlagProperties(
        Cache cache,
        Internal internal,
        Scheduling scheduling,
        GrpcClients grpcClients
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

    /**
     * The x-internal-token scheduler-service's own GrpcAuthInterceptor
     * expects on every RPC but Ping - see config.GrpcClientConfig for where
     * this gets attached. Must match scheduler-service's own
     * {@code arceus.scheduler.internal.api-token} exactly.
     */
    public record GrpcClients(
            String schedulerToken
    ) {
    }
}
