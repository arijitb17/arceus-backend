package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.entity.JobTriggerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyServiceTest {

    private final SchedulerProperties properties = new SchedulerProperties(
            new SchedulerProperties.Poll(1000, 50),
            new SchedulerProperties.Worker(10, 50, 500),
            new SchedulerProperties.Retry(3, 30_000L, 2.0, true, 1_800_000L),
            new SchedulerProperties.Callback(3000, 10000),
            new SchedulerProperties.Delay(true, 3_600_000L),
            new SchedulerProperties.Internal("test-token")
    );

    private final RetryPolicyService retryPolicyService = new RetryPolicyService(properties);

    private Job jobWith(int retryCount, int maxRetries, long retryDelayMs, double multiplier, boolean exponential) {
        return Job.builder()
                .jobName("test-job")
                .serviceName("test-service")
                .triggerType(JobTriggerType.DELAY)
                .delayMs(1000L)
                .executionTarget(ExecutionTarget.HTTP_CALLBACK)
                .callbackUrl("http://example.com")
                .status(JobStatus.RETRYING)
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .retryDelayMs(retryDelayMs)
                .backoffMultiplier(multiplier)
                .exponentialBackoff(exponential)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void canRetryWhenUnderMaxRetries() {
        assertTrue(retryPolicyService.canRetry(jobWith(1, 3, 1000, 2.0, false)));
    }

    @Test
    void cannotRetryWhenAtMaxRetries() {
        assertFalse(retryPolicyService.canRetry(jobWith(3, 3, 1000, 2.0, false)));
    }

    @Test
    void fixedDelayIgnoresRetryCount() {
        Job job = jobWith(0, 5, 1000, 2.0, false);
        assertEquals(1000, retryPolicyService.nextDelayMs(job));
        job.setRetryCount(3);
        assertEquals(1000, retryPolicyService.nextDelayMs(job));
    }

    @Test
    void exponentialBackoffGrowsWithRetryCount() {
        Job job = jobWith(0, 5, 1000, 2.0, true);
        assertEquals(1000, retryPolicyService.nextDelayMs(job)); // 1000 * 2^0
        job.setRetryCount(1);
        assertEquals(2000, retryPolicyService.nextDelayMs(job)); // 1000 * 2^1
        job.setRetryCount(3);
        assertEquals(8000, retryPolicyService.nextDelayMs(job)); // 1000 * 2^3
    }

    @Test
    void exponentialBackoffIsCappedByMaxBackoffMs() {
        Job job = jobWith(20, 30, 1000, 2.0, true); // 1000 * 2^20 would be enormous
        assertEquals(properties.retry().maxBackoffMs(), retryPolicyService.nextDelayMs(job));
    }
}
