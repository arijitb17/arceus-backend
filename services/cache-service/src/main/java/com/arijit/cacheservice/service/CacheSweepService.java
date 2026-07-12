package com.arijit.cacheservice.service;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.core.CacheEntryMetadata;
import com.arijit.cacheservice.dto.TtlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The "clean cache" scheduled job. Every other {@link TtlPolicy} already
 * self-expires natively in Redis - data+meta keys are always
 * written/expired together (see CacheKeyBuilder's Javadoc) - so sweeping
 * those here would just be redundant work racing Redis's own expiry.
 * {@link TtlPolicy#NO_EXPIRY} is the one policy nothing else ever cleans
 * up: a caller asked for a permanent entry, but "permanent" left forgotten
 * forever is exactly how a cache namespace quietly turns into a memory
 * leak. This sweeps NO_EXPIRY entries that haven't been written to
 * (see {@link CacheEntryMetadata#updatedAt()}) in longer than
 * {@code arceus.cache.sweep.no-expiry-stale-after-seconds}.
 * <p>
 * Invoked by scheduler-service's HTTP_CALLBACK executor via
 * SweepCallbackController, on a recurring job SweepJobRegistrar registers
 * once at startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheSweepService {

    private static final String META_SUFFIX = ":__meta";
    private static final int SCAN_BATCH_SIZE = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheStatsService cacheStatsService;
    private final CacheProperties cacheProperties;

    public long sweepStaleNoExpiryEntries() {
        Duration staleAfter = Duration.ofSeconds(cacheProperties.sweep().noExpiryStaleAfterSeconds());
        Instant cutoff = Instant.now().minus(staleAfter);
        long evicted = 0;

        ScanOptions options = ScanOptions.scanOptions().match("*" + META_SUFFIX).count(SCAN_BATCH_SIZE).build();
        try (var cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String metaKey = cursor.next();
                if (evictIfStaleNoExpiry(metaKey, cutoff)) {
                    evicted++;
                }
            }
        }

        if (evicted > 0) {
            cacheStatsService.recordEvictions("scheduled-sweep:no-expiry-stale", evicted);
            log.info("Cache sweep: evicted {} stale NO_EXPIRY entries (untouched for over {})", evicted, staleAfter);
        } else {
            log.debug("Cache sweep: no stale NO_EXPIRY entries found");
        }
        return evicted;
    }

    private boolean evictIfStaleNoExpiry(String metaKey, Instant cutoff) {
        Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(metaKey);
        CacheEntryMetadata metadata = CacheEntryMetadata.fromRedisHash(hash);
        if (metadata == null || metadata.ttlPolicy() != TtlPolicy.NO_EXPIRY || metadata.updatedAt().isAfter(cutoff)) {
            return false;
        }

        String dataKey = metaKey.substring(0, metaKey.length() - META_SUFFIX.length());
        Long deleted = stringRedisTemplate.delete(List.of(dataKey, metaKey));
        return deleted != null && deleted > 0;
    }
}
