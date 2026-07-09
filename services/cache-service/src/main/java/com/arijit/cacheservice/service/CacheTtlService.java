package com.arijit.cacheservice.service;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.dto.TtlPolicy;
import com.arijit.cacheservice.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Turns a {@link TtlPolicy} + its caller-supplied inputs into a single
 * "seconds from now, or no expiry" decision. Kept separate from
 * {@link CacheStoreService} because this is pure policy math with no Redis
 * access - straightforward to unit test on its own.
 */
@Component
@RequiredArgsConstructor
public class CacheTtlService {

    private final CacheProperties cacheProperties;

    /**
     * @return seconds until expiry to apply now, or empty if the key should have no expiry
     */
    public java.util.Optional<Long> resolveTtlSeconds(TtlPolicy policy, Long ttlSeconds, Instant absoluteExpireAt) {
        return switch (policy) {
            case NO_EXPIRY -> java.util.Optional.empty();

            case ABSOLUTE -> {
                if (absoluteExpireAt == null) {
                    throw new ApiException("ttlPolicy=ABSOLUTE requires absoluteExpireAt", HttpStatus.BAD_REQUEST);
                }
                long seconds = absoluteExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
                if (seconds <= 0) {
                    throw new ApiException("absoluteExpireAt must be in the future", HttpStatus.BAD_REQUEST);
                }
                yield java.util.Optional.of(seconds);
            }

            case SLIDING -> {
                long seconds = positiveOrDefault(ttlSeconds, "ttlPolicy=SLIDING");
                yield java.util.Optional.of(seconds);
            }

            case CUSTOM -> {
                long seconds = positiveOrDefault(ttlSeconds, "ttlPolicy=CUSTOM");
                yield java.util.Optional.of(seconds);
            }
        };
    }

    private long positiveOrDefault(Long ttlSeconds, String policyLabel) {
        if (ttlSeconds != null && ttlSeconds > 0) {
            return ttlSeconds;
        }
        long fallback = cacheProperties.defaultTtlSeconds();
        if (fallback > 0) {
            return fallback;
        }
        throw new ApiException(policyLabel + " requires a positive ttlSeconds (no default-ttl-seconds configured)", HttpStatus.BAD_REQUEST);
    }
}
