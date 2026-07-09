package com.arijit.cacheservice.service;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.dto.TtlPolicy;
import com.arijit.cacheservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure policy-math tests - no Redis, no Spring context, which is exactly
 * the point of keeping this logic in its own class.
 */
class CacheTtlServiceTest {

    private final CacheProperties properties = new CacheProperties(
            new CacheProperties.Pool(16, 8, 0, 2000),
            3600L,
            com.arijit.cacheservice.dto.SerializationFormat.JSON,
            5_242_880L,
            new CacheProperties.Compression(true, 8192L),
            new CacheProperties.Namespaces(List.of("session"), false)
    );

    private final CacheTtlService service = new CacheTtlService(properties);

    @Test
    void noExpiryYieldsEmpty() {
        assertTrue(service.resolveTtlSeconds(TtlPolicy.NO_EXPIRY, null, null).isEmpty());
    }

    @Test
    void customUsesGivenTtlSeconds() {
        Optional<Long> result = service.resolveTtlSeconds(TtlPolicy.CUSTOM, 120L, null);
        assertEquals(120L, result.orElseThrow());
    }

    @Test
    void customFallsBackToConfiguredDefaultWhenTtlSecondsMissing() {
        Optional<Long> result = service.resolveTtlSeconds(TtlPolicy.CUSTOM, null, null);
        assertEquals(3600L, result.orElseThrow());
    }

    @Test
    void slidingRequiresPositiveTtlOrDefault() {
        Optional<Long> result = service.resolveTtlSeconds(TtlPolicy.SLIDING, 900L, null);
        assertEquals(900L, result.orElseThrow());
    }

    @Test
    void absoluteComputesSecondsUntilInstant() {
        Instant future = Instant.now().plusSeconds(60);
        Optional<Long> result = service.resolveTtlSeconds(TtlPolicy.ABSOLUTE, null, future);
        assertTrue(result.orElseThrow() > 55 && result.orElseThrow() <= 60);
    }

    @Test
    void absoluteWithoutInstantThrows() {
        assertThrows(ApiException.class, () -> service.resolveTtlSeconds(TtlPolicy.ABSOLUTE, null, null));
    }

    @Test
    void absoluteInThePastThrows() {
        Instant past = Instant.now().minusSeconds(10);
        assertThrows(ApiException.class, () -> service.resolveTtlSeconds(TtlPolicy.ABSOLUTE, null, past));
    }
}
