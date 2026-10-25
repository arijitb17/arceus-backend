package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import com.arijit.monitoringservice.messaging.MonitoringEventPublisher;
import com.arijit.monitoringservice.repository.ServiceHealthSnapshotRepository;
import com.arijit.monitoringservice.repository.ServiceMetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicePollingService {
    private final MonitoringProperties properties;
    private final ServiceHealthCollector healthCollector;
    private final ServiceMetricsCollector metricsCollector;
    private final ServiceHealthSnapshotRepository healthRepository;
    private final ServiceMetricSnapshotRepository metricRepository;
    private final LatestStateCache latestStateCache;
    private final AlertEvaluationService alertEvaluationService;
    private final MonitoringEventPublisher eventPublisher;

    private final Map<String, ServiceStatus> lastKnownStatus = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void pollOnStartup() {
        pollAll();
    }

    @Scheduled(fixedDelayString = "${arceus.monitoring.polling.interval-ms:15000}")
    public void pollAll() {
        Map<String, MonitoringProperties.Target> targets = properties.targets();
        if (targets == null || targets.isEmpty()) {
            log.debug("No monitoring targets configured - nothing to poll");
            return;
        }
        targets.forEach(this::pollOne);
    }

    private void pollOne(String serviceName, MonitoringProperties.Target target) {
        try {
            ServiceHealthSnapshot health = healthCollector.collect(serviceName, target);
            health = healthRepository.save(health);
            latestStateCache.putHealth(serviceName, health);
            handleTransition(serviceName, health);

            ServiceMetricSnapshot metrics = metricsCollector.collect(serviceName, target);
            metrics = metricRepository.save(metrics);
            latestStateCache.putMetrics(serviceName, metrics);

            alertEvaluationService.evaluate(serviceName, health, metrics);
        } catch (Exception e) {
            // A failure anywhere in this target's pipeline (collector, DB write, alert evaluation)
            // must never propagate and skip the remaining targets in this cycle - each service's
            // own poll is independent.
            log.error("Polling cycle failed for {}: {}", serviceName, e.getMessage(), e);
        }
    }

    private void handleTransition(String serviceName, ServiceHealthSnapshot health) {
        ServiceStatus previous = lastKnownStatus.put(serviceName, health.getStatus());
        if (previous == null) {
            return;
        }
        if (previous != ServiceStatus.DOWN && health.getStatus() == ServiceStatus.DOWN) {
            eventPublisher.serviceDown(serviceName, health.getErrorMessage());
        } else if (previous == ServiceStatus.DOWN && health.getStatus() != ServiceStatus.DOWN) {
            eventPublisher.serviceRecovered(serviceName);
        }
    }
}
