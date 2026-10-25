package com.arijit.cacheservice.config;

import com.arijit.cacheservice.dto.SerializationFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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

    public record Sweep(
            long noExpiryStaleAfterSeconds
    ) {
    }

    public record Internal(
            String apiToken,
            String callbackBaseUrl
    ) {
    }

    public record Scheduling(
            long sweepIntervalMs,
            String schedulerToken
    ) {
    }
}
