package com.arijit.cacheservice.service;

import com.arijit.cacheservice.core.CacheKeyBuilder;
import com.arijit.cacheservice.core.NamespaceValidator;
import com.arijit.cacheservice.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CacheEvictionServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock NamespaceValidator validator;
    @Mock CacheStatsService stats;
    @Mock Cursor<String> cursor;

    private CacheEvictionService service;

    @BeforeEach
    void setUp() {
        service = new CacheEvictionService(redis, new CacheKeyBuilder(), validator, stats);
    }

    private void cursorReturns(List<String> keys) {
        var it = keys.iterator();
        when(cursor.hasNext()).thenAnswer(i -> it.hasNext());
        lenient().when(cursor.next()).thenAnswer(i -> it.next());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
    }

    @Test
    void evictKeyDeletesDataAndMetaAndRecordsStat() {
        when(redis.delete(List.of("ns:k", "ns:k:__meta"))).thenReturn(2L);

        assertThat(service.evictKey("ns", "k")).isTrue();

        verify(validator).validate("ns");
        verify(stats).recordEvictions("manual:ns", 1);
    }

    @Test
    void evictKeyReturnsFalseWhenNothingDeleted() {
        when(redis.delete(any(Collection.class))).thenReturn(0L);

        assertThat(service.evictKey("ns", "missing")).isFalse();

        verify(stats, never()).recordEvictions(anyString(), anyLong());
    }

    @Test
    void evictKeyHandlesNullFromRedis() {
        when(redis.delete(any(Collection.class))).thenReturn(null);

        assertThat(service.evictKey("ns", "k")).isFalse();
    }

    @Test
    void evictKeyPropagatesInvalidNamespace() {
        doThrow(new ApiException("bad", HttpStatus.BAD_REQUEST)).when(validator).validate("bad ns");

        assertThatThrownBy(() -> service.evictKey("bad ns", "k")).isInstanceOf(ApiException.class);
        verifyNoInteractions(redis);
    }

    @Test
    void evictNamespaceScansPrefixAndDeletesMatches() {
        cursorReturns(List.of("ns:a", "ns:a:__meta", "ns:b"));
        when(redis.delete(any(Collection.class))).thenReturn(3L);

        long n = service.evictNamespace("ns");

        assertThat(n).isEqualTo(3);
        ArgumentCaptor<ScanOptions> opts = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(opts.capture());
        assertThat(opts.getValue().getPattern()).isEqualTo("ns:*");
        verify(stats).recordEvictions("namespace:ns", 3);
        verify(cursor).close();
    }

    @Test
    void evictNamespaceWithNoMatchesDeletesNothing() {
        cursorReturns(List.of());

        assertThat(service.evictNamespace("ns")).isZero();

        verify(redis, never()).delete(any(Collection.class));
        verify(stats).recordEvictions("namespace:ns", 0);
    }

    @Test
    void deletesInBatchesOf500() {
        List<String> keys = IntStream.range(0, 1200).mapToObj(i -> "ns:" + i).toList();
        cursorReturns(keys);
        List<Integer> batchSizes = new ArrayList<>();
        when(redis.delete(any(Collection.class))).thenAnswer(inv -> {
            Collection<String> batch = inv.getArgument(0);
            batchSizes.add(batch.size());
            return (long) batch.size();
        });

        long n = service.evictNamespace("ns");

        assertThat(n).isEqualTo(1200);
        assertThat(batchSizes).containsExactly(500, 500, 200);
    }

    @Test
    void evictPatternValidatesScopeBeforeScanning() {
        doThrow(new ApiException("unscoped", HttpStatus.BAD_REQUEST)).when(validator).validatePatternIsNamespaceScoped("*");

        assertThatThrownBy(() -> service.evictPattern("*")).isInstanceOf(ApiException.class);
        verifyNoInteractions(redis, stats);
    }

    @Test
    void evictPatternUsesGivenPattern() {
        cursorReturns(List.of("session:user:1"));
        when(redis.delete(any(Collection.class))).thenReturn(1L);

        assertThat(service.evictPattern("session:user:*")).isEqualTo(1);

        ArgumentCaptor<ScanOptions> opts = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(opts.capture());
        assertThat(opts.getValue().getPattern()).isEqualTo("session:user:*");
        verify(stats).recordEvictions("pattern:session:user:*", 1);
    }
}
