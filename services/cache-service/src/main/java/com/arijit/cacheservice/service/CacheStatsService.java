package com.arijit.cacheservice.service;

import com.arijit.cacheservice.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Hit/miss/eviction counters are kept as plain Redis INCR counters (not
 * in-process) so stats stay accurate and shared across every cache-service
 * instance behind a load balancer, and survive individual instance
 * restarts. {@code totalKeys}/{@code memoryUsageBytes}/{@code expiredKeys}
 * come straight from Redis's own DBSIZE and INFO output - global to the
 * Redis logical database this service is configured against, not filtered
 * to keys this service wrote, since Redis doesn't track that distinction
 * for us without Pub/Sub keyspace notifications (deliberately out of scope
 * for now).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheStatsService {

    private static final String HITS_KEY = "__arceus_cache_stats:hits";
    private static final String MISSES_KEY = "__arceus_cache_stats:misses";
    private static final String EVICTIONS_KEY = "__arceus_cache_stats:evictions";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public void recordHit(String namespace, String key) {
        stringRedisTemplate.opsForValue().increment(HITS_KEY);
        log.debug("cache hit namespace={} key={}", namespace, key);
    }

    public void recordMiss(String namespace, String key) {
        stringRedisTemplate.opsForValue().increment(MISSES_KEY);
        log.debug("cache miss namespace={} key={}", namespace, key);
    }

    public void recordEvictions(String scope, long count) {
        if (count <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(EVICTIONS_KEY, count);
        log.info("cache eviction scope={} keysEvicted={}", scope, count);
    }

    public StatsResponse stats() {
        long hits = longOrZero(stringRedisTemplate.opsForValue().get(HITS_KEY));
        long misses = longOrZero(stringRedisTemplate.opsForValue().get(MISSES_KEY));
        long evictions = longOrZero(stringRedisTemplate.opsForValue().get(EVICTIONS_KEY));
        double hitRatio = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses);

        long totalKeys = 0;
        long memoryUsageBytes = 0;
        long expiredKeys = 0;
        try {
            var connection = redisConnectionFactory.getConnection();
            try {
                Long dbSize = connection.serverCommands().dbSize();
                totalKeys = dbSize != null ? dbSize : 0;

                Properties memoryInfo = connection.serverCommands().info("memory");
                if (memoryInfo != null) {
                    memoryUsageBytes = parseLongProperty(memoryInfo, "used_memory");
                }

                Properties statsInfo = connection.serverCommands().info("stats");
                if (statsInfo != null) {
                    expiredKeys = parseLongProperty(statsInfo, "expired_keys");
                }
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("Could not read live Redis stats (DBSIZE/INFO): {}", e.getMessage());
        }

        return new StatsResponse(hits, misses, hitRatio, totalKeys, memoryUsageBytes, expiredKeys, evictions);
    }

    private long longOrZero(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }

    private long parseLongProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
