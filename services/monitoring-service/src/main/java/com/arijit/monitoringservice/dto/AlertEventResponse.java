package com.arijit.monitoringservice.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertEventResponse(
        UUID id,
        String ruleName,
        String type,
        String serviceName,
        String severity,
        String message,
        String state,
        double metricValue,
        Instant triggeredAt,
        Instant resolvedAt
) {
}
