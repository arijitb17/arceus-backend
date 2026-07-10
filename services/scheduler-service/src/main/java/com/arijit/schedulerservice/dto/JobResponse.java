package com.arijit.schedulerservice.dto;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.entity.JobTriggerType;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String jobName,
        String serviceName,
        JobTriggerType triggerType,
        String cronExpression,
        Long delayMs,
        Long fixedRateMs,
        Long fixedDelayMs,
        ExecutionTarget executionTarget,
        String callbackUrl,
        String targetExchange,
        String targetRoutingKey,
        String payload,
        int priority,
        JobStatus status,
        int retryCount,
        int maxRetries,
        long retryDelayMs,
        double backoffMultiplier,
        boolean exponentialBackoff,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        Instant nextFireAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        String lastError
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getJobName(),
                job.getServiceName(),
                job.getTriggerType(),
                job.getCronExpression(),
                job.getDelayMs(),
                job.getFixedRateMs(),
                job.getFixedDelayMs(),
                job.getExecutionTarget(),
                job.getCallbackUrl(),
                job.getTargetExchange(),
                job.getTargetRoutingKey(),
                job.getPayload(),
                job.getPriority(),
                job.getStatus(),
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getRetryDelayMs(),
                job.getBackoffMultiplier(),
                job.isExponentialBackoff(),
                job.getCorrelationId(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getNextFireAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getExpiresAt(),
                job.getLastError()
        );
    }
}
