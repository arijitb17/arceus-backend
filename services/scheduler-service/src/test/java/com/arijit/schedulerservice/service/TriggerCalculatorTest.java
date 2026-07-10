package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.entity.JobTriggerType;
import com.arijit.schedulerservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TriggerCalculatorTest {

    private final TriggerCalculator calculator = new TriggerCalculator();

    private Job.JobBuilder baseJob() {
        return Job.builder()
                .jobName("test-job")
                .serviceName("test-service")
                .executionTarget(ExecutionTarget.HTTP_CALLBACK)
                .callbackUrl("http://example.com")
                .status(JobStatus.SCHEDULED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    @Test
    void immediateFiresNow() {
        Job job = baseJob().triggerType(JobTriggerType.IMMEDIATE).build();
        Instant fireAt = calculator.firstFireAt(job);
        assertTrue(fireAt.isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void delayFiresAfterDelayMs() {
        Job job = baseJob().triggerType(JobTriggerType.DELAY).delayMs(60_000L).build();
        Instant fireAt = calculator.firstFireAt(job);
        Instant expected = Instant.now().plusSeconds(60);
        assertTrue(Math.abs(fireAt.until(expected, ChronoUnit.SECONDS)) < 2);
    }

    @Test
    void delayWithoutDelayMsIsRejected() {
        Job job = baseJob().triggerType(JobTriggerType.DELAY).build();
        assertThrows(ApiException.class, () -> calculator.firstFireAt(job));
    }

    @Test
    void cronComputesNextOccurrence() {
        Job job = baseJob().triggerType(JobTriggerType.CRON).cronExpression("0 0 0 * * *").build();
        Instant fireAt = calculator.firstFireAt(job);
        assertTrue(fireAt.isAfter(Instant.now()));
    }

    @Test
    void invalidCronIsRejected() {
        Job job = baseJob().triggerType(JobTriggerType.CRON).cronExpression("not a cron").build();
        assertThrows(ApiException.class, () -> calculator.firstFireAt(job));
    }

    @Test
    void fixedRateMeasuresFromPreviousScheduledTime() {
        Instant previousFire = Instant.now().minusSeconds(30);
        Job job = baseJob().triggerType(JobTriggerType.FIXED_RATE).fixedRateMs(60_000L).nextFireAt(previousFire).build();
        Instant next = calculator.nextRecurringFireAt(job);
        assertEquals(previousFire.plusMillis(60_000L), next);
    }

    @Test
    void fixedDelayMeasuresFromNow() {
        Job job = baseJob().triggerType(JobTriggerType.FIXED_DELAY).fixedDelayMs(60_000L).build();
        Instant next = calculator.nextRecurringFireAt(job);
        Instant expected = Instant.now().plusSeconds(60);
        assertTrue(Math.abs(next.until(expected, ChronoUnit.SECONDS)) < 2);
    }

    @Test
    void nonRecurringTriggerCannotComputeNextRecurringFire() {
        Job job = baseJob().triggerType(JobTriggerType.DELAY).delayMs(1000L).build();
        assertThrows(IllegalStateException.class, () -> calculator.nextRecurringFireAt(job));
    }
}
