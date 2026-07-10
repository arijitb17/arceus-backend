package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fixed delay or exponential backoff, per job (job.retryDelayMs /
 * job.backoffMultiplier / job.exponentialBackoff), capped by the
 * platform-wide {@code arceus.scheduler.retry.max-backoff-ms} so a
 * misconfigured multiplier can't push a retry weeks into the future.
 */
@Component
@RequiredArgsConstructor
public class RetryPolicyService {

    private final SchedulerProperties properties;

    /** Delay before the upcoming attempt, given how many attempts have already happened (0 = first retry). */
    public long nextDelayMs(Job job) {
        long base = job.getRetryDelayMs();
        if (!job.isExponentialBackoff()) {
            return Math.min(base, properties.retry().maxBackoffMs());
        }
        double multiplier = Math.pow(job.getBackoffMultiplier(), job.getRetryCount());
        long delay = Math.round(base * multiplier);
        return Math.min(delay, properties.retry().maxBackoffMs());
    }

    public boolean canRetry(Job job) {
        return job.getRetryCount() < job.getMaxRetries();
    }
}
