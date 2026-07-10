package com.arijit.schedulerservice.dto;

import com.arijit.schedulerservice.entity.JobExecutionHistory;
import com.arijit.schedulerservice.entity.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobHistoryResponse(
        UUID id,
        UUID jobId,
        String jobName,
        String serviceName,
        int attemptNumber,
        JobStatus resultStatus,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String errorMessage,
        String correlationId
) {
    public static JobHistoryResponse from(JobExecutionHistory h) {
        return new JobHistoryResponse(
                h.getId(), h.getJobId(), h.getJobName(), h.getServiceName(), h.getAttemptNumber(),
                h.getResultStatus(), h.getStartedAt(), h.getCompletedAt(), h.getDurationMs(),
                h.getErrorMessage(), h.getCorrelationId()
        );
    }
}
