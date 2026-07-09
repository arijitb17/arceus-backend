package com.arijit.cacheservice.core;

import org.springframework.stereotype.Component;

/**
 * Central place that decides Redis key layout, so nothing else in the
 * codebase concatenates namespace/key strings by hand.
 * <p>
 * Data lives at {@code {namespace}:{key}} using whatever native Redis
 * structure {@link com.arijit.cacheservice.dto.CacheDataType} calls for.
 * A small metadata hash lives alongside it at {@code {namespace}:{key}:__meta},
 * recording the type/serialization/compression/TTL-policy needed to read
 * the entry back correctly and to support sliding TTL renewal - see
 * {@link CacheEntryMetadata}. Both keys are always created and expired
 * together.
 */
@Component
public class CacheKeyBuilder {

    private static final String META_SUFFIX = ":__meta";

    public String dataKey(String namespace, String key) {
        return namespace + ":" + key;
    }

    public String metaKey(String namespace, String key) {
        return dataKey(namespace, key) + META_SUFFIX;
    }

    /** Prefix used for namespace-wide SCAN/eviction; matches both data and meta keys. */
    public String namespacePrefix(String namespace) {
        return namespace + ":*";
    }
}
