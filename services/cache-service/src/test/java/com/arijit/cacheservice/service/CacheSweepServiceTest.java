package com.arijit.cacheservice.service;

import com.arijit.cacheservice.CacheTestFixtures;
import com.arijit.cacheservice.core.CacheEntryMetadata;
import com.arijit.cacheservice.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CacheSweepServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock CacheStatsService stats;
    @Mock Cursor<String> cursor;
    @Mock HashOperations<String, Object, Object> hashOps;

    private CacheSweepService service;

    @BeforeEach
    void setUp() {
        service = new CacheSweepService(redis, stats, CacheTestFixtures.properties());
    }

    private Map<Object, Object> meta(TtlPolicy policy, Instant updatedAt) {
        return new HashMap<Object, Object>(new CacheEntryMetadata(CacheDataType.STRING, SerializationFormat.JSON, false,
                policy, 0, CachePolicy.CACHE_ASIDE, updatedAt.minusSeconds(10), updatedAt).toRedisHash());
    }

    private void scanReturns(String... keys) {
        var it = List.of(keys).iterator();
        when(cursor.hasNext()).thenAnswer(i -> it.hasNext());
        when(cursor.next()).thenAnswer(i -> it.next());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        doReturn(hashOps).when(redis).opsForHash();
    }

    @Test
    void evictsOnlyStaleNoExpiryEntries() {
        Instant stale = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant fresh = Instant.now().minusSeconds(60);
        scanReturns("ns:old:__meta", "ns:new:__meta", "ns:ttl:__meta");
        when(hashOps.entries("ns:old:__meta")).thenReturn(meta(TtlPolicy.NO_EXPIRY, stale));
        when(hashOps.entries("ns:new:__meta")).thenReturn(meta(TtlPolicy.NO_EXPIRY, fresh));
        when(hashOps.entries("ns:ttl:__meta")).thenReturn(meta(TtlPolicy.CUSTOM, stale));
        when(redis.delete(List.of("ns:old", "ns:old:__meta"))).thenReturn(2L);

        long evicted = service.sweepStaleNoExpiryEntries();

        assertThat(evicted).isEqualTo(1);
        verify(redis, times(1)).delete(any(java.util.Collection.class));
        verify(stats).recordEvictions("scheduled-sweep:no-expiry-stale", 1);
        verify(cursor).close();
    }

    @Test
    void ignoresMetaKeysWithoutHash() {
        scanReturns("ns:gone:__meta");
        when(hashOps.entries("ns:gone:__meta")).thenReturn(new HashMap<>());

        assertThat(service.sweepStaleNoExpiryEntries()).isZero();

        verify(redis, never()).delete(any(java.util.Collection.class));
        verify(stats, never()).recordEvictions(anyString(), anyLong());
    }

    @Test
    void doesNotCountKeysRedisFailedToDelete() {
        scanReturns("ns:old:__meta");
        when(hashOps.entries(anyString())).thenReturn(meta(TtlPolicy.NO_EXPIRY, Instant.now().minus(90, ChronoUnit.DAYS)));
        when(redis.delete(any(java.util.Collection.class))).thenReturn(0L);

        assertThat(service.sweepStaleNoExpiryEntries()).isZero();
    }

    @Test
    void scansForMetaSuffix() {
        when(cursor.hasNext()).thenReturn(false);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        service.sweepStaleNoExpiryEntries();

        var captor = org.mockito.ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(captor.capture());
        assertThat(captor.getValue().getPattern()).isEqualTo("*:__meta");
    }
}
