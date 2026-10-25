package com.arijit.cacheservice.service;

import com.arijit.cacheservice.dto.StatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheStatsServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock RedisConnectionFactory factory;
    @Mock RedisConnection connection;
    @Mock RedisServerCommands server;

    private CacheStatsService service;

    @BeforeEach
    void setUp() {
        service = new CacheStatsService(redis, factory);
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void recordsHitsAndMissesAsCounters() {
        service.recordHit("ns", "k");
        service.recordMiss("ns", "k");

        verify(ops).increment("__arceus_cache_stats:hits");
        verify(ops).increment("__arceus_cache_stats:misses");
    }

    @Test
    void recordEvictionsIgnoresNonPositiveCounts() {
        service.recordEvictions("scope", 0);
        service.recordEvictions("scope", -4);
        service.recordEvictions("scope", 7);

        verify(ops, times(1)).increment("__arceus_cache_stats:evictions", 7L);
        verifyNoMoreInteractions(ops);
    }

    private void stubLiveRedis(Long dbSize, String usedMemory, String expired) {
        when(factory.getConnection()).thenReturn(connection);
        when(connection.serverCommands()).thenReturn(server);
        when(server.dbSize()).thenReturn(dbSize);
        Properties mem = new Properties();
        if (usedMemory != null) mem.setProperty("used_memory", usedMemory);
        Properties st = new Properties();
        if (expired != null) st.setProperty("expired_keys", expired);
        when(server.info("memory")).thenReturn(mem);
        when(server.info("stats")).thenReturn(st);
    }

    @Test
    void statsCombineCountersAndLiveRedisInfo() {
        when(ops.get("__arceus_cache_stats:hits")).thenReturn("75");
        when(ops.get("__arceus_cache_stats:misses")).thenReturn("25");
        when(ops.get("__arceus_cache_stats:evictions")).thenReturn("3");
        stubLiveRedis(120L, " 2048 ", "9");

        StatsResponse s = service.stats();

        assertThat(s.hitCount()).isEqualTo(75);
        assertThat(s.missCount()).isEqualTo(25);
        assertThat(s.hitRatio()).isEqualTo(0.75);
        assertThat(s.totalKeys()).isEqualTo(120);
        assertThat(s.memoryUsageBytes()).isEqualTo(2048);
        assertThat(s.expiredKeys()).isEqualTo(9);
        assertThat(s.evictionCount()).isEqualTo(3);
        verify(connection).close();
    }

    @Test
    void emptyCountersGiveZeroRatioNotNaN() {
        when(ops.get(anyString())).thenReturn(null);
        stubLiveRedis(null, null, null);

        StatsResponse s = service.stats();

        assertThat(s.hitRatio()).isZero();
        assertThat(s.totalKeys()).isZero();
        assertThat(s.memoryUsageBytes()).isZero();
    }

    @Test
    void unparsableInfoValueBecomesZero() {
        when(ops.get(anyString())).thenReturn("1");
        stubLiveRedis(1L, "n/a", "oops");

        StatsResponse s = service.stats();

        assertThat(s.memoryUsageBytes()).isZero();
        assertThat(s.expiredKeys()).isZero();
    }

    @Test
    void redisInfoFailureStillReturnsCounters() {
        when(ops.get("__arceus_cache_stats:hits")).thenReturn("4");
        when(ops.get("__arceus_cache_stats:misses")).thenReturn("1");
        when(ops.get("__arceus_cache_stats:evictions")).thenReturn(null);
        when(factory.getConnection()).thenThrow(new IllegalStateException("redis down"));

        StatsResponse s = service.stats();

        assertThat(s.hitCount()).isEqualTo(4);
        assertThat(s.hitRatio()).isEqualTo(0.8);
        assertThat(s.totalKeys()).isZero();
    }
}
