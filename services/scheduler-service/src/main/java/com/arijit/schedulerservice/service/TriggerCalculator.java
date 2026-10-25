package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobTriggerType;
import com.arijit.schedulerservice.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class TriggerCalculator {
    public Instant firstFireAt(Job job) {
        Instant now = Instant.now();
        return switch (job.getTriggerType()) {
            case IMMEDIATE -> now;
            case DELAY -> now.plusMillis(requireField(job.getDelayMs(), "delayMs", job.getTriggerType()));
            case CRON -> nextCronFireAfter(requireCron(job), now);
            case FIXED_RATE -> now.plusMillis(requireField(job.getFixedRateMs(), "fixedRateMs", job.getTriggerType()));
            case FIXED_DELAY -> now.plusMillis(requireField(job.getFixedDelayMs(), "fixedDelayMs", job.getTriggerType()));
        };
    }

    public Instant nextRecurringFireAt(Job job) {
        Instant reference = job.getNextFireAt() != null ? job.getNextFireAt() : Instant.now();
        return switch (job.getTriggerType()) {
            case CRON -> nextCronFireAfter(requireCron(job), Instant.now());
            case FIXED_RATE -> reference.plusMillis(requireField(job.getFixedRateMs(), "fixedRateMs", job.getTriggerType()));
            case FIXED_DELAY -> Instant.now().plusMillis(requireField(job.getFixedDelayMs(), "fixedDelayMs", job.getTriggerType()));
            default -> throw new IllegalStateException("nextRecurringFireAt called for non-recurring trigger " + job.getTriggerType());
        };
    }

    public Instant nextRetryFireAt(Job job, long delayMs) {
        return Instant.now().plusMillis(delayMs);
    }

    private Instant nextCronFireAfter(String cron, Instant after) {
        CronExpression parsed = parseCron(cron);
        LocalDateTime next = parsed.next(LocalDateTime.ofInstant(after, ZoneOffset.UTC));
        if (next == null) {
            throw new ApiException("Cron expression '" + cron + "' has no future fire time", HttpStatus.BAD_REQUEST);
        }
        return next.toInstant(ZoneOffset.UTC);
    }

    public CronExpression parseCron(String cron) {
        try {
            return CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Invalid cron expression '" + cron + "': " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private String requireCron(Job job) {
        if (job.getCronExpression() == null || job.getCronExpression().isBlank()) {
            throw new ApiException("cronExpression is required for CRON jobs", HttpStatus.BAD_REQUEST);
        }
        return job.getCronExpression();
    }

    private long requireField(Long value, String fieldName, JobTriggerType triggerType) {
        if (value == null) {
            throw new ApiException(fieldName + " is required for " + triggerType + " jobs", HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}
