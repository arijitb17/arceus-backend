package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.dto.PlatformHealthResponse;
import com.arijit.monitoringservice.dto.ServiceDetailResponse;
import com.arijit.monitoringservice.entity.*;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.messaging.MonitoringEventPublisher;
import com.arijit.monitoringservice.repository.ServiceHealthSnapshotRepository;
import com.arijit.monitoringservice.repository.ServiceMetricSnapshotRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformHealthAndPollingTest {

    @Mock ServiceHealthSnapshotRepository healthRepo;
    @Mock ServiceMetricSnapshotRepository metricRepo;
    @Mock ServiceHealthCollector healthCollector;
    @Mock ServiceMetricsCollector metricsCollector;
    @Mock AlertEvaluationService alertEvaluation;
    @Mock MonitoringEventPublisher publisher;

    private ServiceHealthSnapshot health(String name, ServiceStatus status) {
        return ServiceHealthSnapshot.builder().serviceName(name).status(status)
                .databaseStatus(ComponentStatus.UP).cacheStatus(ComponentStatus.UP).rabbitStatus(ComponentStatus.UP)
                .grpcStatus(ComponentStatus.NOT_APPLICABLE).apiAvailable(status != ServiceStatus.DOWN)
                .responseTimeMs(12).uptimeSeconds(99).version("1.0").errorMessage(status == ServiceStatus.DOWN ? "boom" : null)
                .checkedAt(Instant.now()).build();
    }

    private MonitoringProperties props(Map<String, MonitoringProperties.Target> targets) {
        return new MonitoringProperties(new MonitoringProperties.Internal("t"), new MonitoringProperties.Polling(1000, 500),
                new MonitoringProperties.Thresholds(80, 80, 5, 1000), targets);
    }

    @Nested
    class PlatformHealth {

        private final Map<String, MonitoringProperties.Target> targets = Map.of("auth", new MonitoringProperties.Target("u", "h", 1));

        @Test
        void emptyCacheIsUnknown() {
            PlatformHealthResponse r = new PlatformHealthService(new LatestStateCache(), healthRepo, props(targets)).getPlatformHealth();

            assertThat(r.overallStatus()).isEqualTo("UNKNOWN");
            assertThat(r.services()).isEmpty();
        }

        @Test
        void overallStatusFollowsWorstService() {
            LatestStateCache cache = new LatestStateCache();
            PlatformHealthService svc = new PlatformHealthService(cache, healthRepo, props(targets));

            cache.putHealth("b", health("b", ServiceStatus.UP));
            cache.putHealth("a", health("a", ServiceStatus.UP));
            assertThat(svc.getPlatformHealth().overallStatus()).isEqualTo("UP");
            assertThat(svc.getPlatformHealth().services()).extracting(s -> s.serviceName()).containsExactly("a", "b");

            cache.putHealth("c", health("c", ServiceStatus.DEGRADED));
            assertThat(svc.getPlatformHealth().overallStatus()).isEqualTo("DEGRADED");

            cache.putHealth("d", health("d", ServiceStatus.DOWN));
            assertThat(svc.getPlatformHealth().overallStatus()).isEqualTo("DOWN");
        }

        @Test
        void detailForUnknownServiceIs404() {
            PlatformHealthService svc = new PlatformHealthService(new LatestStateCache(), healthRepo, props(targets));

            assertThatThrownBy(() -> svc.getServiceDetail("ghost")).isInstanceOf(ApiException.class);
        }

        @Test
        void detailBeforeFirstPollReturnsUnknownPlaceholder() {
            when(healthRepo.findByServiceNameOrderByCheckedAtDesc(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            ServiceDetailResponse d = new PlatformHealthService(new LatestStateCache(), healthRepo, props(targets)).getServiceDetail("auth");

            assertThat(d.current().status()).isEqualTo("UNKNOWN");
            assertThat(d.current().apiAvailable()).isFalse();
            assertThat(d.history()).isEmpty();
        }

        @Test
        void detailIncludesCurrentAndHistory() {
            LatestStateCache cache = new LatestStateCache();
            cache.putHealth("auth", health("auth", ServiceStatus.UP));
            when(healthRepo.findByServiceNameOrderByCheckedAtDesc(eq("auth"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(health("auth", ServiceStatus.DOWN), health("auth", ServiceStatus.UP))));

            ServiceDetailResponse d = new PlatformHealthService(cache, healthRepo, props(targets)).getServiceDetail("auth");

            assertThat(d.current().status()).isEqualTo("UP");
            assertThat(d.history()).extracting(h -> h.status()).containsExactly("DOWN", "UP");
            assertThat(d.current().grpcStatus()).isEqualTo("NOT_APPLICABLE");
        }
    }

    @Nested
    class Polling {

        private final MonitoringProperties.Target target = new MonitoringProperties.Target("u", "h", 1);

        private ServicePollingService service(Map<String, MonitoringProperties.Target> targets, LatestStateCache cache) {
            return new ServicePollingService(props(targets), healthCollector, metricsCollector, healthRepo, metricRepo,
                    cache, alertEvaluation, publisher);
        }

        private void stubCollect(String name, ServiceStatus status) {
            ServiceHealthSnapshot h = health(name, status);
            when(healthCollector.collect(eq(name), any())).thenReturn(h);
            when(healthRepo.save(h)).thenReturn(h);
            ServiceMetricSnapshot m = ServiceMetricSnapshot.builder().serviceName(name).build();
            when(metricsCollector.collect(eq(name), any())).thenReturn(m);
            when(metricRepo.save(m)).thenReturn(m);
        }

        @Test
        void noTargetsMeansNoWork() {
            service(Map.of(), new LatestStateCache()).pollAll();
            service(null, new LatestStateCache()).pollAll();

            verifyNoInteractions(healthCollector, metricsCollector);
        }

        @Test
        void pollPersistsCachesAndEvaluatesAlerts() {
            LatestStateCache cache = new LatestStateCache();
            stubCollect("auth", ServiceStatus.UP);

            service(Map.of("auth", target), cache).pollAll();

            assertThat(cache.getHealth("auth")).isPresent();
            assertThat(cache.getMetrics("auth")).isPresent();
            verify(alertEvaluation).evaluate(eq("auth"), any(), any());
            verifyNoInteractions(publisher);
        }

        @Test
        void publishesOnlyOnStateTransitions() {
            ServicePollingService svc = service(Map.of("auth", target), new LatestStateCache());
            ServiceHealthSnapshot up = health("auth", ServiceStatus.UP);
            ServiceHealthSnapshot down = health("auth", ServiceStatus.DOWN);
            ServiceMetricSnapshot m = ServiceMetricSnapshot.builder().serviceName("auth").build();
            when(healthCollector.collect(eq("auth"), any())).thenReturn(up, down, down, up);
            when(healthRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(metricsCollector.collect(eq("auth"), any())).thenReturn(m);
            when(metricRepo.save(any())).thenReturn(m);

            svc.pollAll();
            verifyNoInteractions(publisher);
            svc.pollAll();
            verify(publisher, times(1)).serviceDown("auth", "boom");
            svc.pollAll();
            verify(publisher, times(1)).serviceDown(any(), any());
            svc.pollAll();
            verify(publisher).serviceRecovered("auth");
        }

        @Test
        void oneFailingTargetDoesNotStopTheOthers() {
            Map<String, MonitoringProperties.Target> targets = new LinkedHashMap<>();
            targets.put("broken", target);
            targets.put("auth", target);
            when(healthCollector.collect(eq("broken"), any())).thenThrow(new IllegalStateException("boom"));
            stubCollect("auth", ServiceStatus.UP);

            assertThatCode(() -> service(targets, new LatestStateCache()).pollAll()).doesNotThrowAnyException();

            verify(alertEvaluation).evaluate(eq("auth"), any(), any());
            verify(alertEvaluation, never()).evaluate(eq("broken"), any(), any());
        }

        @Test
        void startupHookPollsImmediately() {
            stubCollect("auth", ServiceStatus.UP);

            service(Map.of("auth", target), new LatestStateCache()).pollOnStartup();

            verify(healthCollector).collect(eq("auth"), any());
        }
    }
}
