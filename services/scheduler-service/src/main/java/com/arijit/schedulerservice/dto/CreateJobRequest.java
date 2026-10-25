package com.arijit.schedulerservice.dto;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.JobTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record CreateJobRequest(
        @NotBlank String jobName,
        @NotBlank String serviceName,
        @NotNull JobTriggerType triggerType,
        String cronExpression,
        @PositiveOrZero Long delayMs,
        @PositiveOrZero Long fixedRateMs,
        @PositiveOrZero Long fixedDelayMs,
        @NotNull ExecutionTarget executionTarget,
        String callbackUrl,
        String targetExchange,
        String targetRoutingKey,
        String payload,
        int priority,
        Integer maxRetries,
        Long retryDelayMs,
        Double backoffMultiplier,
        Boolean exponentialBackoff,
        String correlationId,
        Instant expiresAt
) {
}
