package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.entity.AlertRule;
import com.arijit.monitoringservice.entity.AlertSeverity;
import com.arijit.monitoringservice.entity.AlertType;
import com.arijit.monitoringservice.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAlertRuleSeeder implements ApplicationRunner {
    private static final String SEEDED_BY = "system";

    private final AlertRuleRepository alertRuleRepository;
    private final MonitoringProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (alertRuleRepository.count() > 0) {
            return;
        }
        MonitoringProperties.Thresholds t = properties.thresholds();
        log.info("Seeding default platform-wide alert rules");

        seed("Service Down", AlertType.SERVICE_DOWN, 0, AlertSeverity.CRITICAL,
                "Fires when a monitored service's health check reports DOWN.");
        seed("High CPU Usage", AlertType.HIGH_CPU, t.cpuPercent(), AlertSeverity.WARNING,
                "Fires when a service's CPU usage exceeds the threshold.");
        seed("High Memory Usage", AlertType.HIGH_MEMORY, t.memoryPercent(), AlertSeverity.WARNING,
                "Fires when a service's heap usage exceeds the threshold.");
        seed("High Error Rate", AlertType.HIGH_ERROR_RATE, t.errorRatePercent(), AlertSeverity.CRITICAL,
                "Fires when a service's HTTP error rate exceeds the threshold.");
        seed("High Response Time", AlertType.HIGH_RESPONSE_TIME, t.responseTimeMs(), AlertSeverity.WARNING,
                "Fires when a service's average response time exceeds the threshold.");
        seed("RabbitMQ Offline", AlertType.RABBITMQ_OFFLINE, 0, AlertSeverity.CRITICAL,
                "Fires when a service reports its RabbitMQ connection as DOWN.");
        seed("Redis Offline", AlertType.REDIS_OFFLINE, 0, AlertSeverity.WARNING,
                "Fires when a service reports its Redis connection as DOWN.");
        seed("Database Offline", AlertType.DATABASE_OFFLINE, 0, AlertSeverity.CRITICAL,
                "Fires when a service reports its database connection as DOWN.");
    }

    private void seed(String name, AlertType type, double threshold, AlertSeverity severity, String description) {
        Instant now = Instant.now();
        AlertRule rule = AlertRule.builder()
                .name(name)
                .type(type)
                .serviceName(null)
                .thresholdValue(threshold)
                .severity(severity)
                .enabled(true)
                .description(description)
                .createdBy(SEEDED_BY)
                .createdAt(now)
                .updatedAt(now)
                .build();
        alertRuleRepository.save(rule);
    }
}
