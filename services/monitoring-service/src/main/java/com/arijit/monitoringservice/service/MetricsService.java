package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.dto.ServiceMetricsDetailResponse;
import com.arijit.monitoringservice.dto.ServiceMetricsDto;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.ServiceMetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final LatestStateCache latestStateCache;
    private final ServiceMetricSnapshotRepository metricRepository;
    private final MonitoringProperties properties;

    public List<ServiceMetricsDto> getAllLatestMetrics() {
        return latestStateCache.allMetrics().stream()
                .map(this::toDto)
                .sorted((a, b) -> a.serviceName().compareTo(b.serviceName()))
                .toList();
    }

    public ServiceMetricsDetailResponse getServiceMetrics(String serviceName) {
        validateKnownService(serviceName);

        ServiceMetricsDto current = latestStateCache.getMetrics(serviceName)
                .map(this::toDto)
                .orElse(null);

        List<ServiceMetricsDto> history = metricRepository
                .findByServiceNameOrderByCapturedAtDesc(serviceName, PageRequest.of(0, DEFAULT_HISTORY_LIMIT, Sort.by("capturedAt").descending()))
                .stream()
                .map(this::toDto)
                .toList();

        return new ServiceMetricsDetailResponse(current, history);
    }

    private void validateKnownService(String serviceName) {
        Set<String> known = properties.targets() != null ? properties.targets().keySet() : Set.of();
        if (!known.contains(serviceName)) {
            throw new ApiException("Unknown service: " + serviceName, HttpStatus.NOT_FOUND);
        }
    }

    private ServiceMetricsDto toDto(ServiceMetricSnapshot m) {
        return new ServiceMetricsDto(
                m.getServiceName(),
                m.getRequestCount(),
                m.getErrorCount(),
                m.getSuccessRate(),
                m.getAvgResponseTimeMs(),
                m.getCpuUsagePercent(),
                m.getMemoryUsedMb(),
                m.getMemoryMaxMb(),
                m.getJvmHeapUsedMb(),
                m.getThreadCount(),
                m.getActiveConnections(),
                m.getQueueSize(),
                m.getCacheHitRatio(),
                m.getCacheMissRatio(),
                m.getSchedulerActiveJobs(),
                m.getSchedulerFailedJobs(),
                m.getFeatureFlagEvaluations(),
                m.getRateLimitRequests(),
                m.getRateLimitBlocked(),
                m.getCapturedAt()
        );
    }
}
