package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetryPolicyService {
    private final SchedulerProperties properties;

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
