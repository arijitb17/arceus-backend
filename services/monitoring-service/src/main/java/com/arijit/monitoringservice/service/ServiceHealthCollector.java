package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.entity.ComponentStatus;
import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;

/**
 * Performs one health check against one monitored service, per the spec's
 * Health Checks list: Service Status, Database Status, Redis Status,
 * RabbitMQ Status, gRPC Connectivity, API Availability, Response Time,
 * Uptime.
 * <p>
 * Deliberately talks to every monitored service the same way - Spring Boot
 * Actuator's {@code GET /actuator/health} with
 * {@code management.endpoint.health.show-details=always} - rather than each
 * service's own bespoke {@code /health} controller (whose response shape
 * differs service to service, e.g. cache-service's {@code redisStatus} vs
 * scheduler-service's {@code rabbitMqStatus}). Actuator's {@code
 * components.<name>.status} breakdown is uniform across every Spring Boot
 * app regardless of which optional dependencies it has, so this collector
 * never needs a per-service parser.
 * <p>
 * gRPC connectivity is checked at the TCP level only (can a socket be
 * opened to the configured grpc host/port) rather than a typed Ping RPC,
 * because every monitored service defines its own, differently-shaped gRPC
 * service (FeatureFlagGrpcService, SchedulerGrpcService, ...) - a uniform
 * typed check would mean this collector depending on and maintaining a stub
 * per service. A TCP-level check is a weaker signal (it confirms the port
 * accepts connections, not that the gRPC server behind it is fully healthy)
 * but is genuinely protocol-agnostic and never needs updating as new
 * services are added.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceHealthCollector {

    private static final String DB_COMPONENT = "db";
    private static final String REDIS_COMPONENT = "redis";
    private static final String RABBIT_COMPONENT = "rabbit";
    private static final int GRPC_CONNECT_TIMEOUT_MS = 1500;

    private final RestClient monitoringRestClient;
    private final MonitoringProperties properties;

    public ServiceHealthSnapshot collect(String serviceName, MonitoringProperties.Target target) {
        long start = System.nanoTime();
        Instant checkedAt = Instant.now();

        ServiceHealthSnapshot.ServiceHealthSnapshotBuilder builder = ServiceHealthSnapshot.builder()
                .serviceName(serviceName)
                .checkedAt(checkedAt)
                .databaseStatus(ComponentStatus.NOT_APPLICABLE)
                .cacheStatus(ComponentStatus.NOT_APPLICABLE)
                .rabbitStatus(ComponentStatus.NOT_APPLICABLE);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = monitoringRestClient.get()
                    .uri(target.baseUrl() + "/actuator/health")
                    .retrieve()
                    .body(Map.class);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            builder.responseTimeMs(elapsedMs).apiAvailable(true);

            String topLevelStatus = body != null ? String.valueOf(body.getOrDefault("status", "UNKNOWN")) : "UNKNOWN";
            builder.status(mapStatus(topLevelStatus));

            @SuppressWarnings("unchecked")
            Map<String, Object> components = body != null
                    ? (Map<String, Object>) body.getOrDefault("components", Map.of())
                    : Map.of();

            builder.databaseStatus(componentStatus(components, DB_COMPONENT));
            builder.cacheStatus(componentStatus(components, REDIS_COMPONENT));
            builder.rabbitStatus(componentStatus(components, RABBIT_COMPONENT));
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("Health check failed for {}: {}", serviceName, e.getMessage());
            builder.responseTimeMs(elapsedMs)
                    .apiAvailable(false)
                    .status(ServiceStatus.DOWN)
                    .errorMessage(truncate(e.getMessage()));
        }

        builder.uptimeSeconds(fetchUptimeSeconds(target));
        builder.version(fetchVersion(target));
        builder.grpcStatus(checkGrpcConnectivity(target));

        return builder.build();
    }

    private ComponentStatus componentStatus(Map<String, Object> components, String name) {
        Object component = components.get(name);
        if (!(component instanceof Map<?, ?> map)) {
            return ComponentStatus.NOT_APPLICABLE;
        }
        Object status = map.get("status");
        if (status == null) {
            return ComponentStatus.UNKNOWN;
        }
        return "UP".equalsIgnoreCase(status.toString()) ? ComponentStatus.UP : ComponentStatus.DOWN;
    }

    private ServiceStatus mapStatus(String actuatorStatus) {
        return switch (actuatorStatus.toUpperCase()) {
            case "UP" -> ServiceStatus.UP;
            case "DOWN", "OUT_OF_SERVICE" -> ServiceStatus.DOWN;
            case "UNKNOWN" -> ServiceStatus.UNKNOWN;
            default -> ServiceStatus.DEGRADED;
        };
    }

    private long fetchUptimeSeconds(MonitoringProperties.Target target) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = monitoringRestClient.get()
                    .uri(target.baseUrl() + "/actuator/metrics/process.uptime")
                    .retrieve()
                    .body(Map.class);
            return Math.round(MetricParsing.firstMeasurementValue(body).orElse(0.0));
        } catch (Exception e) {
            return 0L;
        }
    }

    private String fetchVersion(MonitoringProperties.Target target) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = monitoringRestClient.get()
                    .uri(target.baseUrl() + "/actuator/info")
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return "unknown";
            }
            Object build = body.get("build");
            if (build instanceof Map<?, ?> buildMap && buildMap.get("version") != null) {
                return String.valueOf(buildMap.get("version"));
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private ComponentStatus checkGrpcConnectivity(MonitoringProperties.Target target) {
        if (target.grpcHost() == null || target.grpcHost().isBlank() || target.grpcPort() <= 0) {
            return ComponentStatus.NOT_APPLICABLE;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.grpcHost(), target.grpcPort()), GRPC_CONNECT_TIMEOUT_MS);
            return ComponentStatus.UP;
        } catch (Exception e) {
            return ComponentStatus.DOWN;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unreachable";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
