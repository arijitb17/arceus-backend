package com.arijit.monitoringservice.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        String name,
        String type,
        String serviceName,
        double thresholdValue,
        String severity,
        boolean enabled,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
