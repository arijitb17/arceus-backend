package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.entity.AlertEvent;
import com.arijit.monitoringservice.entity.AlertRule;
import com.arijit.monitoringservice.entity.AlertState;
import com.arijit.monitoringservice.entity.ComponentStatus;
import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import com.arijit.monitoringservice.messaging.MonitoringEventPublisher;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import com.arijit.monitoringservice.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * After every polling cycle, ServicePollingService calls {@link
 * #evaluate} once per monitored service with that service's freshest
 * health + metric snapshot. Every enabled {@link AlertRule} that applies to
 * that service (platform-wide rules apply to all; scoped rules only to
 * their one service) is checked against the condition its {@link
 * com.arijit.monitoringservice.entity.AlertType} defines.
 * <p>
 * At most one ACTIVE {@link AlertEvent} exists per (rule, service) pair:
 * the first time a condition is observed true, a new ACTIVE event is
 * created and {@code alert.triggered} published; on every later cycle
 * while it's still true, nothing changes (no duplicate events); the first
 * time it's observed false again, the existing ACTIVE event is marked
 * RESOLVED and {@code alert.resolved} published.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluationService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final MonitoringEventPublisher eventPublisher;

    @Transactional
    public void evaluate(String serviceName, ServiceHealthSnapshot health, ServiceMetricSnapshot metrics) {
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();

        for (AlertRule rule : rules) {
            if (rule.getServiceName() != null && !rule.getServiceName().equals(serviceName)) {
                continue;
            }
            Optional<Double> observed = observedValue(rule, health, metrics);
            boolean triggered = isConditionMet(rule, health, observed);
            applyResult(rule, serviceName, triggered, observed.orElse(0.0));
        }
    }

    private boolean isConditionMet(AlertRule rule, ServiceHealthSnapshot health, Optional<Double> observed) {
        return switch (rule.getType()) {
            case SERVICE_DOWN -> health.getStatus() == ServiceStatus.DOWN;
            case DATABASE_OFFLINE -> health.getDatabaseStatus() == ComponentStatus.DOWN;
            case REDIS_OFFLINE -> health.getCacheStatus() == ComponentStatus.DOWN;
            case RABBITMQ_OFFLINE -> health.getRabbitStatus() == ComponentStatus.DOWN;
            case HIGH_CPU, HIGH_MEMORY, HIGH_ERROR_RATE, HIGH_RESPONSE_TIME ->
                    observed.map(v -> v > rule.getThresholdValue()).orElse(false);
        };
    }

    private Optional<Double> observedValue(AlertRule rule, ServiceHealthSnapshot health, ServiceMetricSnapshot metrics) {
        return switch (rule.getType()) {
            case HIGH_CPU -> metrics != null ? Optional.ofNullable(metrics.getCpuUsagePercent()) : Optional.empty();
            case HIGH_MEMORY -> metrics != null && metrics.getMemoryUsedMb() != null && metrics.getMemoryMaxMb() != null && metrics.getMemoryMaxMb() > 0
                    ? Optional.of((metrics.getMemoryUsedMb() / metrics.getMemoryMaxMb()) * 100.0)
                    : Optional.empty();
            case HIGH_ERROR_RATE -> metrics != null ? Optional.ofNullable(metrics.getSuccessRate()).map(rate -> 100.0 - rate) : Optional.empty();
            case HIGH_RESPONSE_TIME -> {
                if (metrics != null && metrics.getAvgResponseTimeMs() != null) {
                    yield Optional.of(metrics.getAvgResponseTimeMs());
                }
                yield Optional.of((double) health.getResponseTimeMs());
            }
            case SERVICE_DOWN, DATABASE_OFFLINE, REDIS_OFFLINE, RABBITMQ_OFFLINE -> Optional.empty();
        };
    }

    private void applyResult(AlertRule rule, String serviceName, boolean triggered, double observedValue) {
        Optional<AlertEvent> existingActive = alertEventRepository.findByRuleIdAndServiceNameAndState(
                rule.getId(), serviceName, AlertState.ACTIVE);

        if (triggered && existingActive.isEmpty()) {
            AlertEvent event = AlertEvent.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .type(rule.getType())
                    .serviceName(serviceName)
                    .severity(rule.getSeverity())
                    .message(buildMessage(rule, serviceName, observedValue))
                    .state(AlertState.ACTIVE)
                    .metricValue(observedValue)
                    .triggeredAt(Instant.now())
                    .build();
            event = alertEventRepository.save(event);
            log.warn("Alert triggered: {} for {}", rule.getName(), serviceName);
            eventPublisher.alertTriggered(event);
        } else if (!triggered && existingActive.isPresent()) {
            AlertEvent event = existingActive.get();
            event.setState(AlertState.RESOLVED);
            event.setResolvedAt(Instant.now());
            alertEventRepository.save(event);
            log.info("Alert resolved: {} for {}", rule.getName(), serviceName);
            eventPublisher.alertResolved(event);
        }
    }

    private String buildMessage(AlertRule rule, String serviceName, double observedValue) {
        return switch (rule.getType()) {
            case SERVICE_DOWN -> serviceName + " is DOWN";
            case DATABASE_OFFLINE -> serviceName + "'s database is unreachable";
            case REDIS_OFFLINE -> serviceName + "'s Redis cache is unreachable";
            case RABBITMQ_OFFLINE -> serviceName + "'s RabbitMQ connection is unreachable";
            case HIGH_CPU -> "%s CPU usage is %.1f%% (threshold %.1f%%)".formatted(serviceName, observedValue, rule.getThresholdValue());
            case HIGH_MEMORY -> "%s memory usage is %.1f%% (threshold %.1f%%)".formatted(serviceName, observedValue, rule.getThresholdValue());
            case HIGH_ERROR_RATE -> "%s error rate is %.1f%% (threshold %.1f%%)".formatted(serviceName, observedValue, rule.getThresholdValue());
            case HIGH_RESPONSE_TIME -> "%s response time is %.0fms (threshold %.0fms)".formatted(serviceName, observedValue, rule.getThresholdValue());
        };
    }
}
