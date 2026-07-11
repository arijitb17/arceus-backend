package com.arijit.monitoringservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.monitoring.AlertEventInfo;
import com.arijit.arceus.grpc.monitoring.GetAlertsRequest;
import com.arijit.arceus.grpc.monitoring.GetAlertsResponse;
import com.arijit.arceus.grpc.monitoring.GetDashboardRequest;
import com.arijit.arceus.grpc.monitoring.GetDashboardResponse;
import com.arijit.arceus.grpc.monitoring.GetMetricsRequest;
import com.arijit.arceus.grpc.monitoring.GetMetricsResponse;
import com.arijit.arceus.grpc.monitoring.GetPlatformHealthRequest;
import com.arijit.arceus.grpc.monitoring.GetPlatformHealthResponse;
import com.arijit.arceus.grpc.monitoring.GetServiceHealthRequest;
import com.arijit.arceus.grpc.monitoring.GetServiceHealthResponse;
import com.arijit.arceus.grpc.monitoring.MonitoringGrpcServiceGrpc;
import com.arijit.arceus.grpc.monitoring.ServiceHealth;
import com.arijit.arceus.grpc.monitoring.ServiceMetrics;
import com.arijit.monitoringservice.dto.DashboardResponse;
import com.arijit.monitoringservice.dto.ServiceDetailResponse;
import com.arijit.monitoringservice.dto.ServiceHealthDto;
import com.arijit.monitoringservice.dto.ServiceMetricsDto;
import com.arijit.monitoringservice.entity.AlertEvent;
import com.arijit.monitoringservice.entity.AlertState;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import com.arijit.monitoringservice.service.DashboardService;
import com.arijit.monitoringservice.service.MetricsService;
import com.arijit.monitoringservice.service.PlatformHealthService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * gRPC face of monitoring-service - every RPC calls straight into the same
 * service layer (PlatformHealthService, MetricsService, DashboardService,
 * AlertEventRepository) the REST controllers use, so REST and gRPC callers
 * see identical data. See monitoring.proto for the wire shapes this adapts
 * to/from, and that file's Javadoc for why alert-rule mutation stays
 * REST-only.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MonitoringGrpcServiceImpl extends MonitoringGrpcServiceGrpc.MonitoringGrpcServiceImplBase {

    private final PlatformHealthService platformHealthService;
    private final MetricsService metricsService;
    private final DashboardService dashboardService;
    private final AlertEventRepository alertEventRepository;

    @Override
    public void getServiceHealth(GetServiceHealthRequest request, StreamObserver<GetServiceHealthResponse> responseObserver) {
        try {
            ServiceDetailResponse detail = platformHealthService.getServiceDetail(request.getServiceName());
            responseObserver.onNext(GetServiceHealthResponse.newBuilder()
                    .setFound(true)
                    .setHealth(toProto(detail.current()))
                    .build());
        } catch (ApiException e) {
            responseObserver.onNext(GetServiceHealthResponse.newBuilder().setFound(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getPlatformHealth(GetPlatformHealthRequest request, StreamObserver<GetPlatformHealthResponse> responseObserver) {
        var platform = platformHealthService.getPlatformHealth();
        var builder = GetPlatformHealthResponse.newBuilder()
                .setOverallStatus(platform.overallStatus())
                .setGeneratedAtEpochMillis(platform.generatedAt().toEpochMilli());
        platform.services().forEach(s -> builder.addServices(toProto(s)));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getMetrics(GetMetricsRequest request, StreamObserver<GetMetricsResponse> responseObserver) {
        var builder = GetMetricsResponse.newBuilder();
        if (request.getServiceName().isBlank()) {
            metricsService.getAllLatestMetrics().forEach(m -> builder.addMetrics(toProto(m)));
        } else {
            var detail = metricsService.getServiceMetrics(request.getServiceName());
            if (detail.current() != null) {
                builder.addMetrics(toProto(detail.current()));
            }
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getDashboard(GetDashboardRequest request, StreamObserver<GetDashboardResponse> responseObserver) {
        DashboardResponse dashboard = dashboardService.getDashboard();
        var builder = GetDashboardResponse.newBuilder()
                .setOverallStatus(dashboard.overallStatus())
                .setTotalServices(dashboard.services().size())
                .setServicesUp((int) dashboard.services().stream().filter(s -> "UP".equals(s.status())).count())
                .setServicesDown((int) dashboard.services().stream().filter(s -> "DOWN".equals(s.status())).count())
                .setServicesDegraded((int) dashboard.services().stream().filter(s -> "DEGRADED".equals(s.status())).count())
                .setGeneratedAtEpochMillis(dashboard.generatedAt().toEpochMilli());
        dashboard.services().forEach(s -> builder.addServices(toProto(s)));
        dashboard.activeAlerts().forEach(a -> builder.addActiveAlerts(toProto(a)));
        dashboard.recentAlerts().forEach(a -> builder.addRecentAlerts(toProto(a)));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getAlerts(GetAlertsRequest request, StreamObserver<GetAlertsResponse> responseObserver) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggeredAt"));

        Page<AlertEvent> result;
        boolean hasState = !request.getState().isBlank();
        boolean hasService = !request.getServiceName().isBlank();
        if (hasState && hasService) {
            result = alertEventRepository.findByStateAndServiceName(
                    AlertState.valueOf(request.getState().toUpperCase()), request.getServiceName(), pageable);
        } else if (hasState) {
            result = alertEventRepository.findByState(AlertState.valueOf(request.getState().toUpperCase()), pageable);
        } else if (hasService) {
            result = alertEventRepository.findByServiceName(request.getServiceName(), pageable);
        } else {
            result = alertEventRepository.findAll(pageable);
        }

        var builder = GetAlertsResponse.newBuilder()
                .setTotalElements(result.getTotalElements())
                .setTotalPages(result.getTotalPages())
                .setPage(result.getNumber())
                .setSize(result.getSize());
        result.getContent().forEach(a -> builder.addAlerts(toProto(a)));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("monitoring-service").build());
        responseObserver.onCompleted();
    }

    private ServiceHealth toProto(ServiceHealthDto dto) {
        return ServiceHealth.newBuilder()
                .setServiceName(dto.serviceName())
                .setStatus(dto.status())
                .setDatabaseStatus(dto.databaseStatus())
                .setCacheStatus(dto.cacheStatus())
                .setRabbitStatus(dto.rabbitStatus())
                .setGrpcStatus(dto.grpcStatus())
                .setApiAvailable(dto.apiAvailable())
                .setResponseTimeMs(dto.responseTimeMs())
                .setUptimeSeconds(dto.uptimeSeconds())
                .setVersion(dto.version() != null ? dto.version() : "")
                .setErrorMessage(dto.errorMessage() != null ? dto.errorMessage() : "")
                .setCheckedAtEpochMillis(dto.checkedAt().toEpochMilli())
                .build();
    }

    private ServiceMetrics toProto(ServiceMetricsDto dto) {
        return ServiceMetrics.newBuilder()
                .setServiceName(dto.serviceName())
                .setRequestCount(nz(dto.requestCount()))
                .setErrorCount(nz(dto.errorCount()))
                .setSuccessRate(nz(dto.successRate()))
                .setAvgResponseTimeMs(nz(dto.avgResponseTimeMs()))
                .setCpuUsagePercent(nz(dto.cpuUsagePercent()))
                .setMemoryUsedMb(nz(dto.memoryUsedMb()))
                .setMemoryMaxMb(nz(dto.memoryMaxMb()))
                .setThreadCount(nz(dto.threadCount()))
                .setActiveConnections(nz(dto.activeConnections()))
                .setQueueSize(nz(dto.queueSize()))
                .setCacheHitRatio(nz(dto.cacheHitRatio()))
                .setCacheMissRatio(nz(dto.cacheMissRatio()))
                .setSchedulerActiveJobs(nz(dto.schedulerActiveJobs()))
                .setSchedulerFailedJobs(nz(dto.schedulerFailedJobs()))
                .setFeatureFlagEvaluations(nz(dto.featureFlagEvaluations()))
                .setRateLimitRequests(nz(dto.rateLimitRequests()))
                .setRateLimitBlocked(nz(dto.rateLimitBlocked()))
                .setCapturedAtEpochMillis(dto.capturedAt().toEpochMilli())
                .build();
    }

    private AlertEventInfo toProto(AlertEvent event) {
        return AlertEventInfo.newBuilder()
                .setId(event.getId().toString())
                .setRuleName(event.getRuleName())
                .setAlertType(event.getType().name())
                .setServiceName(event.getServiceName())
                .setSeverity(event.getSeverity().name())
                .setMessage(event.getMessage())
                .setState(event.getState().name())
                .setMetricValue(event.getMetricValue())
                .setTriggeredAtEpochMillis(event.getTriggeredAt().toEpochMilli())
                .setResolvedAtEpochMillis(event.getResolvedAt() != null ? event.getResolvedAt().toEpochMilli() : 0L)
                .build();
    }

    private AlertEventInfo toProto(com.arijit.monitoringservice.dto.AlertEventResponse response) {
        return AlertEventInfo.newBuilder()
                .setId(response.id().toString())
                .setRuleName(response.ruleName())
                .setAlertType(response.type())
                .setServiceName(response.serviceName())
                .setSeverity(response.severity())
                .setMessage(response.message())
                .setState(response.state())
                .setMetricValue(response.metricValue())
                .setTriggeredAtEpochMillis(response.triggeredAt().toEpochMilli())
                .setResolvedAtEpochMillis(response.resolvedAt() != null ? response.resolvedAt().toEpochMilli() : 0L)
                .build();
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }

    private double nz(Double value) {
        return value != null ? value : 0.0;
    }

    private int nz(Integer value) {
        return value != null ? value : 0;
    }
}
