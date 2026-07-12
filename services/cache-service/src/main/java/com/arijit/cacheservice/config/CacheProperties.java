package com.arijit.cacheservice.config;

import com.arijit.cacheservice.dto.SerializationFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code arceus.cache.*} tree in application.yml. Kept as one
 * properties class (rather than one per concern) since every field here is
 * genuinely service-wide configuration - not a candidate for per-namespace
 * override yet, though {@link Namespaces} is the natural place to grow that.
 */
@ConfigurationProperties(prefix = "arceus.cache")
public record CacheProperties(
        Pool pool,
        long defaultTtlSeconds,
        SerializationFormat defaultSerialization,
        long maxObjectSizeBytes,
        Compression compression,
        Namespaces namespaces,
        Sweep sweep,
        Internal internal,
        Scheduling scheduling
) {
    public record Pool(
            int maxActive,
            int maxIdle,
            int minIdle,
            long maxWaitMillis
    ) {
    }

    public record Compression(
            boolean enabled,
            long autoThresholdBytes
    ) {
    }

    public record Namespaces(
            List<String> allowed,
            boolean allowUnlisted
    ) {
    }

    /**
     * "Clean cache" scheduled job - see service.CacheSweepService. Only
     * targets {@link com.arijit.cacheservice.dto.TtlPolicy#NO_EXPIRY}
     * entries: every other TTL policy already self-expires natively in
     * Redis (data+meta keys are always written/expired together - see
     * CacheKeyBuilder's Javadoc), so sweeping those would be redundant.
     * NO_EXPIRY is the one policy Redis alone will never clean up.
     */
    public record Sweep(
            long noExpiryStaleAfterSeconds
    ) {
    }

    /** Shared secret scheduler-service's HTTP_CALLBACK executor presents when it fires the recurring sweep job. */
    public record Internal(
            String apiToken,
            String callbackBaseUrl
    ) {
    }

    public record Scheduling(
            long sweepIntervalMs,
            /** x-internal-token this service must present when it calls scheduler-service's gRPC API - see config.GrpcClientConfig. */
            String schedulerToken
    ) {
    }
}
