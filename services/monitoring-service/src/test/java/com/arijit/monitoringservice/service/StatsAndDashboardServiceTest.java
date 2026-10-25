package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.dto.*;
import com.arijit.monitoringservice.entity.*;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsAndDashboardServiceTest {

    @Mock PlatformHealthService platformHealth;
    @Mock AlertEventRepository alertRepo;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    private final LatestStateCache cache = new LatestStateCache();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private ServiceHealthDto svc(String name, String status) {
        return new ServiceHealthDto(name, status, "UP", "UP", "UP", "UP", true, 10, 100, "1", null, Instant.now());
    }

    private AlertEvent alert() {
        return AlertEvent.builder().id(UUID.randomUUID()).ruleId(UUID.randomUUID()).ruleName("r").type(AlertType.HIGH_CPU)
                .serviceName("auth").severity(AlertSeverity.WARNING).message("m").state(AlertState.ACTIVE)
                .metricValue(91.5).triggeredAt(Instant.now()).build();
    }

    // ---- StatsService ----

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void statsCountsStatusesAndComputesAvailability() {
        when(platformHealth.getPlatformHealth()).thenReturn(new PlatformHealthResponse("DEGRADED",
                List.of(svc("a", "UP"), svc("b", "UP"), svc("c", "DOWN"), svc("d", "DEGRADED")), Instant.now()));
        when(alertRepo.countByState(AlertState.ACTIVE)).thenReturn(2L);
        when(alertRepo.countByStateAndTriggeredAtAfter(eq(AlertState.ACTIVE), any())).thenReturn(3L);
        when(alertRepo.countByStateAndTriggeredAtAfter(eq(AlertState.RESOLVED), any())).thenReturn(4L);
        cache.putMetrics("a", ServiceMetricSnapshot.builder().avgResponseTimeMs(100.0).build());
        cache.putMetrics("b", ServiceMetricSnapshot.builder().avgResponseTimeMs(201.0).build());
        cache.putMetrics("c", ServiceMetricSnapshot.builder().avgResponseTimeMs(null).build());

        StatsResponse s = new StatsService(platformHealth, cache, alertRepo).getStats();

        assertThat(s.totalServices()).isEqualTo(4);
        assertThat(s.servicesUp()).isEqualTo(2);
        assertThat(s.servicesDown()).isEqualTo(1);
        assertThat(s.servicesDegraded()).isEqualTo(1);
        assertThat(s.activeAlertsCount()).isEqualTo(2);
        assertThat(s.alertsTriggeredLast24h()).isEqualTo(7);
        assertThat(s.avgPlatformResponseTimeMs()).isEqualTo(150.5);
        assertThat(s.platformAvailabilityPercentage()).isEqualTo(50.0);
    }

    @Test
    void statsWithNoServicesHasNullAveragesAndAvailability() {
        when(platformHealth.getPlatformHealth()).thenReturn(new PlatformHealthResponse("UNKNOWN", List.of(), Instant.now()));

        StatsResponse s = new StatsService(platformHealth, cache, alertRepo).getStats();

        assertThat(s.totalServices()).isZero();
        assertThat(s.platformAvailabilityPercentage()).isNull();
        assertThat(s.avgPlatformResponseTimeMs()).isNull();
    }

    // ---- DashboardService ----

    private DashboardService dashboard() {
        return new DashboardService(platformHealth, cache, alertRepo, redis, mapper);
    }

    private void stubCompute() {
        when(platformHealth.getPlatformHealth()).thenReturn(new PlatformHealthResponse("UP", List.of(svc("a", "UP")), Instant.now()));
        when(alertRepo.findByState(eq(AlertState.ACTIVE), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(alert())));
        when(alertRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(alert(), alert())));
        cache.putMetrics("a", ServiceMetricSnapshot.builder().requestCount(100L).errorCount(5L).successRate(95.0)
                .avgResponseTimeMs(120.0).cpuUsagePercent(40.0).memoryUsedMb(256.0).memoryMaxMb(512.0).threadCount(30).build());
        cache.putMetrics("b", ServiceMetricSnapshot.builder().requestCount(50L).errorCount(null).successRate(99.0)
                .avgResponseTimeMs(80.0).cpuUsagePercent(20.0).memoryUsedMb(100.0).memoryMaxMb(512.0).threadCount(10).build());
    }

    @Test
    void dashboardComputesAggregatesAndCachesResult() throws Exception {
        stubCompute();
        when(ops.get("monitoring:dashboard")).thenReturn(null);

        DashboardResponse r = dashboard().getDashboard();

        assertThat(r.overallStatus()).isEqualTo("UP");
        assertThat(r.activeAlerts()).hasSize(1);
        assertThat(r.recentAlerts()).hasSize(2);
        assertThat(r.platformMetrics().totalRequestCount()  ).isEqualTo(150);
        assertThat(r.platformMetrics().totalErrorCount()).isEqualTo(5);
        assertThat(r.platformMetrics().avgSuccessRate()).isEqualTo(97.0);
        assertThat(r.platformMetrics().avgResponseTimeMs()).isEqualTo(100.0);
        assertThat(r.resourceUsage().avgCpuUsagePercent()).isEqualTo(30.0);
        assertThat(r.resourceUsage().totalMemoryUsedMb()).isEqualTo(356.0);
        assertThat(r.resourceUsage().totalMemoryMaxMb()).isEqualTo(1024.0);
        assertThat(r.resourceUsage().totalThreadCount()).isEqualTo(40);
        verify(ops).set(eq("monitoring:dashboard"), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void emptyMetricsGiveNullAveragesAndZeroTotals() {
        when(platformHealth.getPlatformHealth()).thenReturn(new PlatformHealthResponse("UNKNOWN", List.of(), Instant.now()));
        when(alertRepo.findByState(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(alertRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        DashboardResponse r = dashboard().getDashboard();

        assertThat(r.platformMetrics().totalRequestCount()).isZero();
        assertThat(r.platformMetrics().avgSuccessRate()).isNull();
        assertThat(r.resourceUsage().avgCpuUsagePercent()).isNull();
        assertThat(r.resourceUsage().totalThreadCount()).isZero();
    }

    @Test
    void cachedDashboardIsReturnedWithoutRecomputing() throws Exception {
        DashboardResponse cached = new DashboardResponse("UP", List.of(), List.of(), List.of(),
                new PlatformMetricsSummary(1, 0, 100.0, 5.0), new ResourceUsageSummary(1.0, 1.0, 2.0, 3), Instant.now());
        when(ops.get("monitoring:dashboard")).thenReturn(mapper.writeValueAsString(cached));

        DashboardResponse r = dashboard().getDashboard();

        assertThat(r.platformMetrics().totalRequestCount()).isEqualTo(1);
        verifyNoInteractions(platformHealth, alertRepo);
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void corruptCacheEntryFallsBackToCompute() {
        stubCompute();
        when(ops.get("monitoring:dashboard")).thenReturn("{corrupt");

        assertThat(dashboard().getDashboard().overallStatus()).isEqualTo("UP");
    }

    @Test
    void redisOutageDegradesToFreshComputation() {
        stubCompute();
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));

        assertThat(dashboard().getDashboard().overallStatus()).isEqualTo("UP");
    }
}
