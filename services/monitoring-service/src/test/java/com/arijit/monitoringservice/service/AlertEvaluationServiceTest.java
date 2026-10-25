package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.entity.AlertEvent;
import com.arijit.monitoringservice.entity.AlertRule;
import com.arijit.monitoringservice.entity.AlertSeverity;
import com.arijit.monitoringservice.entity.AlertState;
import com.arijit.monitoringservice.entity.AlertType;
import com.arijit.monitoringservice.entity.ComponentStatus;
import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import com.arijit.monitoringservice.entity.ServiceStatus;
import com.arijit.monitoringservice.messaging.MonitoringEventPublisher;
import com.arijit.monitoringservice.repository.AlertEventRepository;
import com.arijit.monitoringservice.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private MonitoringEventPublisher eventPublisher;

    private AlertEvaluationService alertEvaluationService;

    @BeforeEach
    void setUp() {
        alertEvaluationService = new AlertEvaluationService(alertRuleRepository, alertEventRepository, eventPublisher);
    }

    @Test
    void createsAlertEventAndPublishesWhenServiceDownRuleFires() {
        AlertRule rule = platformWideRule(AlertType.SERVICE_DOWN, 0);
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(alertEventRepository.findByRuleIdAndServiceNameAndState(rule.getId(), "auth-service", AlertState.ACTIVE))
                .thenReturn(Optional.empty());
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceHealthSnapshot down = healthSnapshot(ServiceStatus.DOWN);

        alertEvaluationService.evaluate("auth-service", down, null);

        verify(alertEventRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).alertTriggered(any());
        verify(eventPublisher, never()).alertResolved(any());
    }

    @Test
    void resolvesExistingAlertWhenConditionClears() {
        AlertRule rule = platformWideRule(AlertType.SERVICE_DOWN, 0);
        AlertEvent active = AlertEvent.builder()
                .id(UUID.randomUUID())
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .type(AlertType.SERVICE_DOWN)
                .serviceName("auth-service")
                .severity(AlertSeverity.CRITICAL)
                .message("auth-service is DOWN")
                .state(AlertState.ACTIVE)
                .triggeredAt(Instant.now())
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(alertEventRepository.findByRuleIdAndServiceNameAndState(rule.getId(), "auth-service", AlertState.ACTIVE))
                .thenReturn(Optional.of(active));
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceHealthSnapshot up = healthSnapshot(ServiceStatus.UP);

        alertEvaluationService.evaluate("auth-service", up, null);

        assertThat(active.getState()).isEqualTo(AlertState.RESOLVED);
        assertThat(active.getResolvedAt()).isNotNull();
        verify(eventPublisher, times(1)).alertResolved(active);
        verify(eventPublisher, never()).alertTriggered(any());
    }

    @Test
    void highCpuRuleFiresOnlyAboveThreshold() {
        AlertRule rule = platformWideRule(AlertType.HIGH_CPU, 80.0);
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(alertEventRepository.findByRuleIdAndServiceNameAndState(any(), any(), any())).thenReturn(Optional.empty());
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceHealthSnapshot up = healthSnapshot(ServiceStatus.UP);
        ServiceMetricSnapshot belowThreshold = ServiceMetricSnapshot.builder().cpuUsagePercent(50.0).build();
        ServiceMetricSnapshot aboveThreshold = ServiceMetricSnapshot.builder().cpuUsagePercent(95.0).build();

        alertEvaluationService.evaluate("auth-service", up, belowThreshold);
        verify(alertEventRepository, never()).save(any());

        alertEvaluationService.evaluate("auth-service", up, aboveThreshold);
        verify(alertEventRepository, times(1)).save(any());
    }

    private AlertRule platformWideRule(AlertType type, double threshold) {
        return AlertRule.builder()
                .id(UUID.randomUUID())
                .name(type.name())
                .type(type)
                .serviceName(null)
                .thresholdValue(threshold)
                .severity(AlertSeverity.CRITICAL)
                .enabled(true)
                .createdBy("system")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ServiceHealthSnapshot healthSnapshot(ServiceStatus status) {
        return ServiceHealthSnapshot.builder()
                .serviceName("auth-service")
                .status(status)
                .databaseStatus(ComponentStatus.UP)
                .cacheStatus(ComponentStatus.UP)
                .rabbitStatus(ComponentStatus.UP)
                .grpcStatus(ComponentStatus.UP)
                .apiAvailable(status != ServiceStatus.DOWN)
                .responseTimeMs(50)
                .uptimeSeconds(1000)
                .checkedAt(Instant.now())
                .build();
    }
}
