package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.dto.ServiceHealthDto;
import com.arijit.monitoringservice.dto.StatsResponse;
import com.arijit.monitoringservice.entity.AlertState;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final PlatformHealthService platformHealthService;
    private final LatestStateCache latestStateCache;
    private final AlertEventRepository alertEventRepository;

    public StatsResponse getStats() {
        List<ServiceHealthDto> services = platformHealthService.getPlatformHealth().services();

        int up = countStatus(services, ServiceStatus.UP);
        int down = countStatus(services, ServiceStatus.DOWN);
        int degraded = countStatus(services, ServiceStatus.DEGRADED);

        long activeAlerts = alertEventRepository.countByState(AlertState.ACTIVE);
        long triggeredLast24h = alertEventRepository.countByStateAndTriggeredAtAfter(
                AlertState.ACTIVE, Instant.now().minus(24, ChronoUnit.HOURS))
                + alertEventRepository.countByStateAndTriggeredAtAfter(
                AlertState.RESOLVED, Instant.now().minus(24, ChronoUnit.HOURS));

        Double avgResponseTime = averageResponseTime();
        Double availability = services.isEmpty() ? null : (up * 100.0) / services.size();

        return new StatsResponse(
                services.size(),
                up,
                down,
                degraded,
                activeAlerts,
                triggeredLast24h,
                avgResponseTime,
                availability,
                Instant.now()
        );
    }

    private int countStatus(List<ServiceHealthDto> services, ServiceStatus status) {
        return (int) services.stream().filter(s -> status.name().equals(s.status())).count();
    }

    private Double averageResponseTime() {
        List<Double> times = latestStateCache.allMetrics().stream()
                .map(ServiceMetricSnapshot::getAvgResponseTimeMs)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (times.isEmpty()) {
            return null;
        }
        return Math.round(times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0;
    }
}
