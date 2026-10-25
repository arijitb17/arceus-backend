package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.dto.*;
import com.arijit.monitoringservice.entity.*;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertAndStateServicesTest {

    @Mock AlertRuleRepository ruleRepo;
    @Mock ServiceHealthSnapshotRepository healthRepo;
    @Mock ServiceMetricSnapshotRepository metricRepo;

    private AlertRule rule() {
        return AlertRule.builder().id(UUID.randomUUID()).name("CPU").type(AlertType.HIGH_CPU).serviceName("auth")
                .thresholdValue(80).severity(AlertSeverity.WARNING).enabled(true).description("d").createdBy("me")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    // ---- AlertRuleService ----

    @Test
    void createDefaultsEnabledAndNormalisesBlankService() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        when(ruleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AlertRuleResponse r = service.create(new AlertRuleRequest("Rule", AlertType.HIGH_MEMORY, "  ", 90, AlertSeverity.CRITICAL, null, "x"), "admin");

        assertThat(r.enabled()).isTrue();
        assertThat(r.serviceName()).isNull();
        assertThat(r.type()).isEqualTo("HIGH_MEMORY");
        assertThat(r.severity()).isEqualTo("CRITICAL");
        assertThat(r.createdBy()).isEqualTo("admin");
    }

    @Test
    void createHonoursExplicitDisabled() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        when(ruleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(new AlertRuleRequest("R", AlertType.SERVICE_DOWN, "auth", 0, AlertSeverity.INFO, false, null), "a").enabled()).isFalse();
    }

    @Test
    void updateOverwritesFieldsButKeepsEnabledWhenNull() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        AlertRule existing = rule();
        when(ruleRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ruleRepo.save(existing)).thenReturn(existing);

        AlertRuleResponse r = service.update(existing.getId(),
                new AlertRuleRequest("New", AlertType.HIGH_ERROR_RATE, null, 5, AlertSeverity.CRITICAL, null, "nd"));

        assertThat(r.name()).isEqualTo("New");
        assertThat(r.type()).isEqualTo("HIGH_ERROR_RATE");
        assertThat(r.serviceName()).isNull();
        assertThat(r.thresholdValue()).isEqualTo(5);
        assertThat(r.enabled()).isTrue();
        assertThat(r.createdBy()).isEqualTo("me");
    }

    @Test
    void updateCanDisable() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        AlertRule existing = rule();
        when(ruleRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ruleRepo.save(existing)).thenReturn(existing);

        assertThat(service.update(existing.getId(), new AlertRuleRequest("n", AlertType.HIGH_CPU, "auth", 1, AlertSeverity.INFO, false, null)).enabled()).isFalse();
    }

    @Test
    void getAndDeleteMissingAre404() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        UUID id = UUID.randomUUID();
        when(ruleRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiException.class);
        verify(ruleRepo, never()).delete(any());
    }

    @Test
    void deleteAndList() {
        AlertRuleService service = new AlertRuleService(ruleRepo);
        AlertRule existing = rule();
        when(ruleRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ruleRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 10), 1));

        service.delete(existing.getId());
        PagedResponse<AlertRuleResponse> page = service.list(PageRequest.of(0, 10));

        verify(ruleRepo).delete(existing);
        assertThat(page.items()).hasSize(1);
    }

    // ---- LatestStateCache ----

    @Test
    void cacheStoresAndReplacesLatestPerService() {
        LatestStateCache cache = new LatestStateCache();
        ServiceHealthSnapshot first = ServiceHealthSnapshot.builder().serviceName("auth").build();
        ServiceHealthSnapshot second = ServiceHealthSnapshot.builder().serviceName("auth").build();
        ServiceMetricSnapshot metric = ServiceMetricSnapshot.builder().serviceName("auth").build();

        assertThat(cache.getHealth("auth")).isEmpty();
        cache.putHealth("auth", first);
        cache.putHealth("auth", second);
        cache.putMetrics("auth", metric);

        assertThat(cache.getHealth("auth")).containsSame(second);
        assertThat(cache.allHealth()).hasSize(1);
        assertThat(cache.getMetrics("auth")).containsSame(metric);
        assertThat(cache.allMetrics()).containsExactly(metric);
        assertThat(cache.getMetrics("other")).isEmpty();
    }

    // ---- RetentionCleanupService ----

    @Test
    void cleanupDeletesOlderThanRetention() {
        RetentionCleanupService svc = new RetentionCleanupService(healthRepo, metricRepo);
        ReflectionTestUtils.setField(svc, "retentionDays", 7);
        Instant before = Instant.now();

        svc.cleanupOldSnapshots();

        ArgumentCaptor<Instant> h = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> m = ArgumentCaptor.forClass(Instant.class);
        verify(healthRepo).deleteByCheckedAtBefore(h.capture());
        verify(metricRepo).deleteByCapturedAtBefore(m.capture());
        assertThat(Duration.between(h.getValue(), before).toDays()).isBetween(6L, 7L);
        assertThat(m.getValue()).isCloseTo(h.getValue(), org.assertj.core.api.Assertions.within(Duration.ofSeconds(2)));
    }

    // ---- DefaultAlertRuleSeeder ----

    private MonitoringProperties props(Map<String, MonitoringProperties.Target> targets) {
        return new MonitoringProperties(new MonitoringProperties.Internal("t"), new MonitoringProperties.Polling(1000, 500),
                new MonitoringProperties.Thresholds(85, 90, 5, 1500), targets);
    }

    @Test
    void seederCreatesEightDefaultRulesWithConfiguredThresholds() {
        when(ruleRepo.count()).thenReturn(0L);

        new DefaultAlertRuleSeeder(ruleRepo, props(Map.of())).run(new DefaultApplicationArguments());

        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(ruleRepo, times(8)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(AlertRule::getType).containsExactly(AlertType.values());
        assertThat(cap.getAllValues()).allSatisfy(r -> {
            assertThat(r.getServiceName()).isNull();
            assertThat(r.isEnabled()).isTrue();
            assertThat(r.getCreatedBy()).isEqualTo("system");
        });
        assertThat(cap.getAllValues().stream().filter(r -> r.getType() == AlertType.HIGH_CPU).findFirst().orElseThrow().getThresholdValue()).isEqualTo(85);
        assertThat(cap.getAllValues().stream().filter(r -> r.getType() == AlertType.HIGH_RESPONSE_TIME).findFirst().orElseThrow().getThresholdValue()).isEqualTo(1500);
        assertThat(cap.getAllValues().stream().filter(r -> r.getType() == AlertType.SERVICE_DOWN).findFirst().orElseThrow().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void seederSkipsWhenRulesExist() {
        when(ruleRepo.count()).thenReturn(3L);

        new DefaultAlertRuleSeeder(ruleRepo, props(Map.of())).run(new DefaultApplicationArguments());

        verify(ruleRepo, never()).save(any());
    }

    // ---- MetricsService ----

    @Test
    void latestMetricsSortedByServiceName() {
        LatestStateCache cache = new LatestStateCache();
        cache.putMetrics("zeta", ServiceMetricSnapshot.builder().serviceName("zeta").build());
        cache.putMetrics("alpha", ServiceMetricSnapshot.builder().serviceName("alpha").build());

        List<ServiceMetricsDto> all = new MetricsService(cache, metricRepo, props(Map.of())).getAllLatestMetrics();

        assertThat(all).extracting(ServiceMetricsDto::serviceName).containsExactly("alpha", "zeta");
    }

    @Test
    void serviceMetricsRejectsUnknownService() {
        MetricsService svc = new MetricsService(new LatestStateCache(), metricRepo, props(Map.of("auth", new MonitoringProperties.Target("u", "h", 1))));

        assertThatThrownBy(() -> svc.getServiceMetrics("ghost")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void serviceMetricsReturnsCurrentAndHistory() {
        LatestStateCache cache = new LatestStateCache();
        ServiceMetricSnapshot current = ServiceMetricSnapshot.builder().serviceName("auth").requestCount(10L).cpuUsagePercent(12.5).build();
        cache.putMetrics("auth", current);
        when(metricRepo.findByServiceNameOrderByCapturedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current, current)));
        MetricsService svc = new MetricsService(cache, metricRepo, props(Map.of("auth", new MonitoringProperties.Target("u", "h", 1))));

        ServiceMetricsDetailResponse r = svc.getServiceMetrics("auth");

        assertThat(r.current().requestCount()).isEqualTo(10L);
        assertThat(r.current().cpuUsagePercent()).isEqualTo(12.5);
        assertThat(r.history()).hasSize(2);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(metricRepo).findByServiceNameOrderByCapturedAtDesc(eq("auth"), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void serviceMetricsWithoutCurrentSnapshotHasNullCurrent() {
        when(metricRepo.findByServiceNameOrderByCapturedAtDesc(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        MetricsService svc = new MetricsService(new LatestStateCache(), metricRepo, props(Map.of("auth", new MonitoringProperties.Target("u", "h", 1))));

        assertThat(svc.getServiceMetrics("auth").current()).isNull();
    }

    @Test
    void nullTargetsMeansEverythingUnknown() {
        MetricsService svc = new MetricsService(new LatestStateCache(), metricRepo, props(null));

        assertThatThrownBy(() -> svc.getServiceMetrics("auth")).isInstanceOf(ApiException.class);
    }
}
