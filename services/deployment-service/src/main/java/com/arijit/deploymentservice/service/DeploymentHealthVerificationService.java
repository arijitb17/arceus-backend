package com.arijit.deploymentservice.service;

import com.arijit.arceus.grpc.monitoring.GetServiceHealthRequest;
import com.arijit.arceus.grpc.monitoring.MonitoringGrpcServiceGrpc;
import com.arijit.arceus.grpc.monitoring.ServiceHealth;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The platform spec's "Health Verification" section, before and after a
 * deploy. Neither pass polls the target service's Actuator endpoint
 * itself - monitoring-service already does exactly that on its own
 * 15-second cycle and is the platform's single source of truth for a
 * service's health (see monitoring-service's ServiceHealthCollector), so
 * both passes here just ask it over gRPC for the latest snapshot. That
 * does mean a check can be up to ~15s stale; for a platform this size
 * that's an acceptable trade against every service re-implementing its
 * own health-polling loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentHealthVerificationService {

    private final MonitoringGrpcServiceGrpc.MonitoringGrpcServiceBlockingStub monitoringStub;

    /**
     * Before a deploy starts: is the target reachable at all, and (if it's
     * already running somewhere) is it currently healthy. A service being
     * deployed for the very first time won't be known to monitoring-service
     * yet - that's expected, not a failure.
     */
    public ValidationResult preDeploy(String serviceName) {
        List<String> warnings = new ArrayList<>();
        try {
            var response = monitoringStub.getServiceHealth(
                    GetServiceHealthRequest.newBuilder().setServiceName(serviceName).build());
            if (!response.getFound()) {
                warnings.add("monitoring-service has no prior health data for '" + serviceName
                        + "' - likely a first-time deployment");
                return new ValidationResult(true, warnings, List.of());
            }
            ServiceHealth health = response.getHealth();
            if (!"UP".equals(health.getStatus())) {
                warnings.add("target service '" + serviceName + "' is currently " + health.getStatus()
                        + " before this deploy even starts");
            }
        } catch (StatusRuntimeException e) {
            warnings.add("could not reach monitoring-service to verify pre-deploy health: " + e.getStatus().getCode());
            log.warn("Pre-deploy health check for {} failed: {}", serviceName, e.getStatus());
        }
        return new ValidationResult(true, warnings, List.of());
    }

    /**
     * After the {@link DeploymentExecutor} reports success: health endpoint
     * responds, gRPC connectivity, database connectivity, RabbitMQ
     * connectivity - all read straight off monitoring-service's latest
     * {@code ServiceHealth} snapshot for this service. Unlike
     * {@link #preDeploy}, an unhealthy result here fails the deployment -
     * this is the check that decides RUNNING vs FAILED.
     */
    public ValidationResult postDeploy(String serviceName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            var response = monitoringStub.getServiceHealth(
                    GetServiceHealthRequest.newBuilder().setServiceName(serviceName).build());
            if (!response.getFound()) {
                warnings.add("monitoring-service has no health data yet for '" + serviceName
                        + "' immediately after deploy - it may not have polled yet");
                return new ValidationResult(true, warnings, List.of());
            }
            ServiceHealth health = response.getHealth();
            if (!health.getApiAvailable()) {
                errors.add("health endpoint for '" + serviceName + "' is not responding");
            }
            if (isDown(health.getGrpcStatus())) {
                errors.add("gRPC connectivity for '" + serviceName + "' is " + health.getGrpcStatus());
            }
            if (isDown(health.getDatabaseStatus())) {
                errors.add("database connectivity for '" + serviceName + "' is " + health.getDatabaseStatus());
            }
            if (isDown(health.getRabbitStatus())) {
                errors.add("RabbitMQ connectivity for '" + serviceName + "' is " + health.getRabbitStatus());
            }
        } catch (StatusRuntimeException e) {
            warnings.add("could not reach monitoring-service to verify post-deploy health: " + e.getStatus().getCode());
            log.warn("Post-deploy health check for {} failed: {}", serviceName, e.getStatus());
        }
        return new ValidationResult(errors.isEmpty(), warnings, errors);
    }

    private boolean isDown(String status) {
        return "DOWN".equals(status);
    }
}
