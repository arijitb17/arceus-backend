package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.dto.AlertEventResponse;
import com.arijit.monitoringservice.dto.DashboardResponse;
import com.arijit.monitoringservice.dto.PlatformMetricsSummary;
import com.arijit.monitoringservice.dto.ResourceUsageSummary;
import com.arijit.monitoringservice.dto.ServiceHealthDto;
import com.arijit.monitoringservice.entity.AlertState;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * If Redis is unavailable this degrades transparently to computing fresh on every call - the
 * dashboard is never allowed to fail just because the cache is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private static final String CACHE_KEY = "monitoring:dashboard";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final int RECENT_ALERTS_LIMIT = 10;

    private final PlatformHealthService platformHealthService;
    private final LatestStateCache latestStateCache;
    private final AlertEventRepository alertEventRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardResponse getDashboard() {
        String cached = tryReadCache();
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached dashboard, recomputing: {}", e.getMessage());
            }
        }

        DashboardResponse response = compute();
        tryWriteCache(response);
        return response;
    }

    private DashboardResponse compute() {
        var platformHealth = platformHealthService.getPlatformHealth();
        List<ServiceHealthDto> services = platformHealth.services();

        List<AlertEventResponse> activeAlerts = alertEventRepository
                .findByState(AlertState.ACTIVE, PageRequest.of(0, 100, Sort.by("triggeredAt").descending()))
                .stream().map(AlertEventMapper::toDto).toList();

        List<AlertEventResponse> recentAlerts = alertEventRepository
                .findAll(PageRequest.of(0, RECENT_ALERTS_LIMIT, Sort.by("triggeredAt").descending()))
                .stream().map(AlertEventMapper::toDto).toList();

        Collection<ServiceMetricSnapshot> metrics = latestStateCache.allMetrics();

        return new DashboardResponse(
                platformHealth.overallStatus(),
                services,
                activeAlerts,
                recentAlerts,
                summarizeMetrics(metrics),
                summarizeResources(metrics),
                Instant.now()
        );
    }

    private PlatformMetricsSummary summarizeMetrics(Collection<ServiceMetricSnapshot> metrics) {
        long totalRequests = metrics.stream().filter(m -> m.getRequestCount() != null).mapToLong(ServiceMetricSnapshot::getRequestCount).sum();
        long totalErrors = metrics.stream().filter(m -> m.getErrorCount() != null).mapToLong(ServiceMetricSnapshot::getErrorCount).sum();
        Double avgSuccessRate = average(metrics.stream().map(ServiceMetricSnapshot::getSuccessRate).toList());
        Double avgResponseTime = average(metrics.stream().map(ServiceMetricSnapshot::getAvgResponseTimeMs).toList());
        return new PlatformMetricsSummary(totalRequests, totalErrors, avgSuccessRate, avgResponseTime);
    }

    private ResourceUsageSummary summarizeResources(Collection<ServiceMetricSnapshot> metrics) {
        Double avgCpu = average(metrics.stream().map(ServiceMetricSnapshot::getCpuUsagePercent).toList());
        double totalMemUsed = metrics.stream().filter(m -> m.getMemoryUsedMb() != null).mapToDouble(ServiceMetricSnapshot::getMemoryUsedMb).sum();
        double totalMemMax = metrics.stream().filter(m -> m.getMemoryMaxMb() != null).mapToDouble(ServiceMetricSnapshot::getMemoryMaxMb).sum();
        int totalThreads = metrics.stream().filter(m -> m.getThreadCount() != null).mapToInt(ServiceMetricSnapshot::getThreadCount).sum();
        return new ResourceUsageSummary(avgCpu, totalMemUsed, totalMemMax, totalThreads);
    }

    private Double average(List<Double> values) {
        List<Double> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return Math.round(present.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0;
    }

    private String tryReadCache() {
        try {
            return stringRedisTemplate.opsForValue().get(CACHE_KEY);
        } catch (Exception e) {
            log.debug("Dashboard cache read skipped (Redis unavailable): {}", e.getMessage());
            return null;
        }
    }

    private void tryWriteCache(DashboardResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            log.debug("Dashboard cache write skipped (Redis unavailable): {}", e.getMessage());
        }
    }
}
