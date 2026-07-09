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
        Namespaces namespaces
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
}
