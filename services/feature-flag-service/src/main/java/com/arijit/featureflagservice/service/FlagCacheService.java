package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.config.FeatureFlagProperties;
import com.arijit.featureflagservice.entity.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caches a flag's serialized state (see FlagService's cache payload
 * format) keyed by key+environment, so EvaluateFlag - the RPC every
 * platform service calls on hot paths - doesn't hit Postgres on every
 * evaluation. Entirely best-effort: any Redis failure (connection
 * refused, timeout, ...) is logged once at WARN and treated as a cache
 * miss, never propagated to the caller - a flag being unreadable from
 * cache must never make evaluation fail, only slightly slower. See
 * HealthService for how Redis's own status is surfaced separately via
 * GET /health, where a caller that cares can actually see it's degraded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlagCacheService {

    private static final String KEY_PREFIX = "featureflag:";

    private final StringRedisTemplate redisTemplate;
    private final FeatureFlagProperties properties;

    public String get(String flagKey, Environment environment) {
        if (!properties.cache().enabled()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(cacheKey(flagKey, environment));
        } catch (Exception e) {
            log.warn("Cache read failed for flag {}/{}: {}", flagKey, environment, e.getMessage());
            return null;
        }
    }

    public void put(String flagKey, Environment environment, String serialized) {
        if (!properties.cache().enabled() || properties.cache().ttlSeconds() <= 0 || serialized == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(flagKey, environment), serialized, Duration.ofSeconds(properties.cache().ttlSeconds()));
        } catch (Exception e) {
            log.warn("Cache write failed for flag {}/{}: {}", flagKey, environment, e.getMessage());
        }
    }

    public void evict(String flagKey, Environment environment) {
        if (!properties.cache().enabled()) {
            return;
        }
        try {
            redisTemplate.delete(cacheKey(flagKey, environment));
        } catch (Exception e) {
            log.warn("Cache eviction failed for flag {}/{}: {}", flagKey, environment, e.getMessage());
        }
    }

    private String cacheKey(String flagKey, Environment environment) {
        return KEY_PREFIX + environment + ":" + flagKey;
    }
}
