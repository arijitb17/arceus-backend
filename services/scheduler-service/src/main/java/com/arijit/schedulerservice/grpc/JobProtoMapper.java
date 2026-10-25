package com.arijit.schedulerservice.grpc;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobTriggerType;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
public class JobProtoMapper {
    public com.arijit.arceus.grpc.scheduler.Job toProto(Job job) {
        var builder = com.arijit.arceus.grpc.scheduler.Job.newBuilder()
                .setId(job.getId().toString())
                .setJobName(job.getJobName())
                .setServiceName(job.getServiceName())
                .setTriggerType(job.getTriggerType().name())
                .setExecutionTarget(job.getExecutionTarget().name())
                .setPriority(job.getPriority())
                .setStatus(job.getStatus().name())
                .setRetryCount(job.getRetryCount())
                .setMaxRetries(job.getMaxRetries())
                .setRetryDelayMs(job.getRetryDelayMs())
                .setBackoffMultiplier(job.getBackoffMultiplier())
                .setExponentialBackoff(job.isExponentialBackoff())
                .setCreatedAtEpochMillis(toEpochMillis(job.getCreatedAt()))
                .setNextFireAtEpochMillis(toEpochMillis(job.getNextFireAt()))
                .setStartedAtEpochMillis(toEpochMillis(job.getStartedAt()))
                .setCompletedAtEpochMillis(toEpochMillis(job.getCompletedAt()))
                .setExpiresAtEpochMillis(toEpochMillis(job.getExpiresAt()));

        if (job.getCronExpression() != null) builder.setCronExpression(job.getCronExpression());
        if (job.getDelayMs() != null) builder.setDelayMs(job.getDelayMs());
        if (job.getFixedRateMs() != null) builder.setFixedRateMs(job.getFixedRateMs());
        if (job.getFixedDelayMs() != null) builder.setFixedDelayMs(job.getFixedDelayMs());
        if (job.getCallbackUrl() != null) builder.setCallbackUrl(job.getCallbackUrl());
        if (job.getTargetExchange() != null) builder.setTargetExchange(job.getTargetExchange());
        if (job.getTargetRoutingKey() != null) builder.setTargetRoutingKey(job.getTargetRoutingKey());
        if (job.getPayload() != null) builder.setPayload(job.getPayload());
        if (job.getCorrelationId() != null) builder.setCorrelationId(job.getCorrelationId());
        if (job.getLastError() != null) builder.setLastError(job.getLastError());

        return builder.build();
    }

    public JobTriggerType parseTriggerType(String value) {
        return JobTriggerType.valueOf(value.toUpperCase());
    }

    public ExecutionTarget parseExecutionTarget(String value) {
        return ExecutionTarget.valueOf(value.toUpperCase());
    }

    private long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
