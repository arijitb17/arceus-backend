package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds just the newest health/metric snapshot per service in memory, so
 * GET /services and GET /dashboard don't need a MAX(checkedAt)-per-service
 * query on every request. ServicePollingService writes through this cache
 * on every poll; every read-side service (PlatformHealthService,
 * MetricsService, DashboardService, StatsService) reads from it.
 * <p>
 * Deliberately plain in-process state (a {@link ConcurrentHashMap}) rather
 * than Redis: Redis in this platform is an <i>optional</i> dependency (per
 * the stack description), and the latest-state cache is single-instance,
 * short-lived, cheaply rebuildable data (the next poll cycle repopulates it
 * within {@code arceus.monitoring.polling.interval-ms}) - exactly the kind
 * of state that doesn't need to survive a restart or be shared across
 * replicas today. Redis remains available on the classpath (see pom.xml)
 * for a future multi-instance deployment to promote this into a shared
 * cache without any public API changing.
 */
@Component
@Slf4j
public class LatestStateCache {

    private final Map<String, ServiceHealthSnapshot> latestHealth = new ConcurrentHashMap<>();
    private final Map<String, ServiceMetricSnapshot> latestMetrics = new ConcurrentHashMap<>();

    public void putHealth(String serviceName, ServiceHealthSnapshot snapshot) {
        latestHealth.put(serviceName, snapshot);
    }

    public void putMetrics(String serviceName, ServiceMetricSnapshot snapshot) {
        latestMetrics.put(serviceName, snapshot);
    }

    public Optional<ServiceHealthSnapshot> getHealth(String serviceName) {
        return Optional.ofNullable(latestHealth.get(serviceName));
    }

    public Optional<ServiceMetricSnapshot> getMetrics(String serviceName) {
        return Optional.ofNullable(latestMetrics.get(serviceName));
    }

    public Collection<ServiceHealthSnapshot> allHealth() {
        return latestHealth.values();
    }

    public Collection<ServiceMetricSnapshot> allMetrics() {
        return latestMetrics.values();
    }
}
