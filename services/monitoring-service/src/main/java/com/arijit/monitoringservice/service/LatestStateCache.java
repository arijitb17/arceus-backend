package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
