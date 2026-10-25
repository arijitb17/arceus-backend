package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.dto.PlatformHealthResponse;
import com.arijit.monitoringservice.dto.ServiceDetailResponse;
import com.arijit.monitoringservice.dto.ServiceHealthDto;
import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.ServiceHealthSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformHealthService {
    private final LatestStateCache latestStateCache;
    private final ServiceHealthSnapshotRepository healthRepository;
    private final MonitoringProperties properties;

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    public PlatformHealthResponse getPlatformHealth() {
        List<ServiceHealthDto> services = latestStateCache.allHealth().stream()
                .map(this::toDto)
                .sorted((a, b) -> a.serviceName().compareTo(b.serviceName()))
                .toList();

        return new PlatformHealthResponse(overallStatus(services), services, Instant.now());
    }

    public ServiceDetailResponse getServiceDetail(String serviceName) {
        validateKnownService(serviceName);

        ServiceHealthDto current = latestStateCache.getHealth(serviceName)
                .map(this::toDto)
                .orElseGet(() -> unknownDto(serviceName));

        List<ServiceHealthDto> history = healthRepository
                .findByServiceNameOrderByCheckedAtDesc(serviceName, PageRequest.of(0, DEFAULT_HISTORY_LIMIT, Sort.by("checkedAt").descending()))
                .stream()
                .map(this::toDto)
                .toList();

        return new ServiceDetailResponse(current, history);
    }

    private void validateKnownService(String serviceName) {
        Set<String> known = properties.targets() != null ? properties.targets().keySet() : Set.of();
        if (!known.contains(serviceName)) {
            throw new ApiException("Unknown service: " + serviceName, HttpStatus.NOT_FOUND);
        }
    }

    private String overallStatus(List<ServiceHealthDto> services) {
        if (services.isEmpty()) {
            return ServiceStatus.UNKNOWN.name();
        }
        boolean anyDown = services.stream().anyMatch(s -> ServiceStatus.DOWN.name().equals(s.status()));
        if (anyDown) {
            return ServiceStatus.DOWN.name();
        }
        boolean anyDegraded = services.stream().anyMatch(s -> ServiceStatus.DEGRADED.name().equals(s.status()));
        return anyDegraded ? ServiceStatus.DEGRADED.name() : ServiceStatus.UP.name();
    }

    private ServiceHealthDto toDto(ServiceHealthSnapshot s) {
        return new ServiceHealthDto(
                s.getServiceName(),
                s.getStatus().name(),
                s.getDatabaseStatus().name(),
                s.getCacheStatus().name(),
                s.getRabbitStatus().name(),
                s.getGrpcStatus().name(),
                s.isApiAvailable(),
                s.getResponseTimeMs(),
                s.getUptimeSeconds(),
                s.getVersion(),
                s.getErrorMessage(),
                s.getCheckedAt()
        );
    }

    private ServiceHealthDto unknownDto(String serviceName) {
        return new ServiceHealthDto(serviceName, ServiceStatus.UNKNOWN.name(), "UNKNOWN", "UNKNOWN", "UNKNOWN",
                "UNKNOWN", false, 0, 0, "unknown", "No health check has completed yet for this service", Instant.now());
    }
}
