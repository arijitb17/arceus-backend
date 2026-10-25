package com.arijit.monitoringservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.monitoringservice.entity.*;
import com.arijit.monitoringservice.repository.UsageRollupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringMessagingTest {

    @Mock UsageRollupRepository repo;
    @Mock RabbitTemplate rabbit;

    private EventEnvelope usage(Map<String, Object> payload) {
        return EventEnvelope.of("USAGE_REQUEST_RECORDED", Instant.now(), "gateway", null, null, null, null, payload);
    }

    private Map<String, Object> payload(UUID project, Object status) {
        Map<String, Object> p = new HashMap<>();
        p.put("projectId", project.toString());
        p.put("environment", "prod");
        p.put("route", "CACHE");
        p.put("statusCode", status);
        return p;
    }

    @Test
    void firstEventCreatesTodaysRollup() {
        UUID project = UUID.randomUUID();
        when(repo.findByProjectIdAndEnvironmentAndRouteAndDay(any(), any(), any(), any())).thenReturn(Optional.empty());

        new UsageRollupConsumer(repo).onUsageEvent(usage(payload(project, 200)));

        ArgumentCaptor<UsageRollup> cap = ArgumentCaptor.forClass(UsageRollup.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getProjectId()).isEqualTo(project);
        assertThat(cap.getValue().getDay()).isEqualTo(LocalDate.now());
        assertThat(cap.getValue().getRequestCount()).isEqualTo(1);
        assertThat(cap.getValue().getErrorCount()).isZero();
    }

    @Test
    void existingRollupIsIncrementedAndErrorsCounted() {
        UUID project = UUID.randomUUID();
        UsageRollup existing = UsageRollup.builder().projectId(project).environment("prod").route("CACHE")
                .day(LocalDate.now()).requestCount(10).errorCount(2).build();
        when(repo.findByProjectIdAndEnvironmentAndRouteAndDay(eq(project), eq("prod"), eq("CACHE"), any())).thenReturn(Optional.of(existing));

        new UsageRollupConsumer(repo).onUsageEvent(usage(payload(project, "503")));

        assertThat(existing.getRequestCount()).isEqualTo(11);
        assertThat(existing.getErrorCount()).isEqualTo(3);
        verify(repo).save(existing);
    }

    @Test
    void unparsableStatusCountsAsSuccessfulRequest() {
        UUID project = UUID.randomUUID();
        when(repo.findByProjectIdAndEnvironmentAndRouteAndDay(any(), any(), any(), any())).thenReturn(Optional.empty());

        new UsageRollupConsumer(repo).onUsageEvent(usage(payload(project, "abc")));

        ArgumentCaptor<UsageRollup> cap = ArgumentCaptor.forClass(UsageRollup.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getErrorCount()).isZero();
    }

    @Test
    void ignoresOtherEventTypesAndMalformedPayloads() {
        UsageRollupConsumer consumer = new UsageRollupConsumer(repo);

        consumer.onUsageEvent(EventEnvelope.of("OTHER", Instant.now(), "s", null, null, null, null, Map.of()));
        Map<String, Object> missing = payload(UUID.randomUUID(), 200);
        missing.remove("route");
        consumer.onUsageEvent(usage(missing));
        Map<String, Object> badId = payload(UUID.randomUUID(), 200);
        badId.put("projectId", "not-a-uuid");
        consumer.onUsageEvent(usage(badId));

        verifyNoInteractions(repo);
    }

    @Test
    void publisherSendsServiceDownAndRecovered() {
        MonitoringEventPublisher publisher = new MonitoringEventPublisher(rabbit);
        ReflectionTestUtils.setField(publisher, "sourceService", "monitoring-service");

        publisher.serviceDown("auth", "timeout");
        publisher.serviceRecovered("auth");

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(ArceusEventsTopology.RK_SERVICE_DOWN), cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("SERVICE_DOWN");
        assertThat(cap.getValue().payload()).containsEntry("serviceName", "auth").containsEntry("errorMessage", "timeout");
        assertThat(cap.getValue().sourceService()).isEqualTo("monitoring-service");
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(ArceusEventsTopology.RK_SERVICE_RECOVERED), any(EventEnvelope.class));
    }

    @Test
    void publisherAlertPayloads() {
        MonitoringEventPublisher publisher = new MonitoringEventPublisher(rabbit);
        ReflectionTestUtils.setField(publisher, "sourceService", "monitoring-service");
        AlertEvent event = AlertEvent.builder().id(UUID.randomUUID()).ruleName("CPU").type(AlertType.HIGH_CPU)
                .serviceName("auth").severity(AlertSeverity.CRITICAL).message("hot").metricValue(97.5).build();

        publisher.alertTriggered(event);
        publisher.alertResolved(AlertEvent.builder().ruleName("x").type(AlertType.SERVICE_DOWN).serviceName("s")
                .severity(AlertSeverity.INFO).message("m").build());

        ArgumentCaptor<EventEnvelope> triggered = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(ArceusEventsTopology.RK_ALERT_TRIGGERED), triggered.capture());
        assertThat(triggered.getValue().payload()).containsEntry("alertEventId", event.getId().toString())
                .containsEntry("type", "HIGH_CPU").containsEntry("severity", "CRITICAL").containsEntry("metricValue", 97.5);
        ArgumentCaptor<EventEnvelope> resolved = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(ArceusEventsTopology.RK_ALERT_RESOLVED), resolved.capture());
        assertThat(resolved.getValue().payload().get("alertEventId")).isNull();
    }

    @Test
    void publisherNeverThrowsOnBrokerFailure() {
        MonitoringEventPublisher publisher = new MonitoringEventPublisher(rabbit);
        doThrow(new AmqpException("down")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.serviceDown("a", "b")).doesNotThrowAnyException();
    }
}
