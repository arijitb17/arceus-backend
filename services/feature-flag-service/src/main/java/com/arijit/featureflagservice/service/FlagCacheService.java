package com.arijit.featureflagservice.service;

import com.arijit.arceus.grpc.cache.CacheGrpcServiceGrpc;
import com.arijit.arceus.grpc.cache.CacheKeyRef;
import com.arijit.arceus.grpc.cache.DeleteResponse;
import com.arijit.arceus.grpc.cache.GetResponse;
import com.arijit.arceus.grpc.cache.PutRequest;
import com.arijit.featureflagservice.config.FeatureFlagProperties;
import com.arijit.featureflagservice.entity.Environment;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * This does add one extra network hop versus a direct Redis call (feature-flag-service -> cache-
 * service -> Redis instead of feature-flag-service -> Redis), which matters here specifically
 * because EvaluateFlag now sits on other services' hot paths too (Gateway's per-route check, Auth's
 * registration gate, ...) - acceptable because it's still a single in-cluster gRPC call, and
 * entirely best-effort: any cache-service failure (down, timeout, ...) is logged once at WARN and
 * treated as a cache miss, never propagated to the caller - a flag being unreadable from cache must
 * never make evaluation fail, only slightly slower.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlagCacheService {
    private static final String NAMESPACE = "featureflag";

    private final CacheGrpcServiceGrpc.CacheGrpcServiceBlockingStub cacheStub;
    private final FeatureFlagProperties properties;

    public String get(String flagKey, Environment environment) {
        if (!properties.cache().enabled()) {
            return null;
        }
        try {
            GetResponse response = cacheStub.get(CacheKeyRef.newBuilder()
                    .setNamespace(NAMESPACE)
                    .setKey(cacheKey(flagKey, environment))
                    .build());
            return response.getFound() ? response.getValue() : null;
        } catch (StatusRuntimeException e) {
            log.warn("Cache read failed for flag {}/{}: {}", flagKey, environment, e.getStatus());
            return null;
        }
    }

    public void put(String flagKey, Environment environment, String serialized) {
        if (!properties.cache().enabled() || properties.cache().ttlSeconds() <= 0 || serialized == null) {
            return;
        }
        try {
            cacheStub.put(PutRequest.newBuilder()
                    .setNamespace(NAMESPACE)
                    .setKey(cacheKey(flagKey, environment))
                    .setValue(serialized)
                    .setSerialization("STRING")
                    .setTtlPolicy("CUSTOM")
                    .setTtlSeconds(properties.cache().ttlSeconds())
                    .build());
        } catch (StatusRuntimeException e) {
            log.warn("Cache write failed for flag {}/{}: {}", flagKey, environment, e.getStatus());
        }
    }

    public void evict(String flagKey, Environment environment) {
        if (!properties.cache().enabled()) {
            return;
        }
        try {
            DeleteResponse response = cacheStub.delete(CacheKeyRef.newBuilder()
                    .setNamespace(NAMESPACE)
                    .setKey(cacheKey(flagKey, environment))
                    .build());
            if (!response.getDeleted()) {
                log.debug("Cache eviction for flag {}/{} was a no-op - key wasn't cached", flagKey, environment);
            }
        } catch (StatusRuntimeException e) {
            log.warn("Cache eviction failed for flag {}/{}: {}", flagKey, environment, e.getStatus());
        }
    }

    private String cacheKey(String flagKey, Environment environment) {
        return environment + ":" + flagKey;
    }
}
