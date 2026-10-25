package com.arijit.monitoringservice.messaging;

import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.monitoringservice.entity.UsageRollup;
import com.arijit.monitoringservice.repository.UsageRollupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsageRollupConsumer {
    private final UsageRollupRepository repository;

    @RabbitListener(queues = "arceus.monitoring.usage-rollup.queue")
    @Transactional
    public void onUsageEvent(EventEnvelope envelope) {
        if (!"USAGE_REQUEST_RECORDED".equals(envelope.eventType())) {
            return;
        }

        Object projectIdObj = envelope.payload().get("projectId");
        Object environmentObj = envelope.payload().get("environment");
        Object routeObj = envelope.payload().get("route");
        Object statusCodeObj = envelope.payload().get("statusCode");

        if (projectIdObj == null || environmentObj == null || routeObj == null) {
            log.warn("Malformed usage event, missing projectId/environment/route: {}", envelope.payload());
            return;
        }

        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdObj.toString());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed usage event, could not parse projectId: {}", projectIdObj);
            return;
        }

        String environment = environmentObj.toString();
        String route = routeObj.toString();
        LocalDate today = LocalDate.now();
        boolean isError = statusCodeObj != null && parseStatus(statusCodeObj) >= 400;

        UsageRollup rollup = repository.findByProjectIdAndEnvironmentAndRouteAndDay(projectId, environment, route, today)
                .orElseGet(() -> UsageRollup.builder()
                        .projectId(projectId)
                        .environment(environment)
                        .route(route)
                        .day(today)
                        .requestCount(0)
                        .errorCount(0)
                        .build());

        rollup.setRequestCount(rollup.getRequestCount() + 1);
        if (isError) {
            rollup.setErrorCount(rollup.getErrorCount() + 1);
        }
        repository.save(rollup);
    }

    private int parseStatus(Object statusCodeObj) {
        try {
            return Integer.parseInt(statusCodeObj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
