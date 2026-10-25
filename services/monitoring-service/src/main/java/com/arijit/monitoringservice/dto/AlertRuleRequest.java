package com.arijit.monitoringservice.dto;

import com.arijit.monitoringservice.entity.AlertSeverity;
import com.arijit.monitoringservice.entity.AlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertRuleRequest(
        @NotBlank String name,
        @NotNull AlertType type,
        String serviceName,
        double thresholdValue,
        @NotNull AlertSeverity severity,
        Boolean enabled,
        String description
) {
}
