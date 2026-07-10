package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per registered job - both its schedule/definition (mostly
 * immutable after creation, mutated only by PUT /jobs/{id} while the job
 * isn't RUNNING) and its live scheduling state (status, retryCount,
 * nextFireAt - mutated constantly by JobPollerService/JobDispatchService).
 * Per-run detail lives in {@link JobExecutionHistory}; this row only ever
 * reflects the *current* run.
 * <p>
 * {@code nextFireAt} is the single field JobPollerService's query filters
 * on - every trigger type (immediate, delay, cron, fixed-rate, fixed-delay)
 * is normalized down to "when does this fire next", so the poller never
 * needs to know or care which trigger type produced that timestamp.
 */
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_job_status_next_fire", columnList = "status,nextFireAt"),
        @Index(name = "idx_job_service_name", columnList = "serviceName"),
        @Index(name = "idx_job_correlation_id", columnList = "correlationId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String jobName;

    /** Which platform service owns this job - auth-service, notification-service, cache-service, ... */
    @Column(nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobTriggerType triggerType;

    /** Used by CRON. Spring's six-field (seconds-first) cron syntax - see TriggerCalculator. */
    @Column(length = 100)
    private String cronExpression;

    /** Used by DELAY - fire delayMs after creation. */
    private Long delayMs;

    /** Used by FIXED_RATE - fire every fixedRateMs, measured from the previous scheduled fire time. */
    private Long fixedRateMs;

    /** Used by FIXED_DELAY - fire fixedDelayMs after the previous run completes. */
    private Long fixedDelayMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionTarget executionTarget;

    /** Used by HTTP_CALLBACK. */
    @Column(length = 2000)
    private String callbackUrl;

    /** Used by MESSAGE_PUBLISH. */
    @Column(length = 200)
    private String targetExchange;

    /** Used by MESSAGE_PUBLISH. */
    @Column(length = 200)
    private String targetRoutingKey;

    /** Arbitrary caller-supplied JSON, delivered verbatim to the execution target. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /**
     * Higher runs first when multiple jobs are due in the same poll batch
     * (see JobPollerService's ordering) - purely a same-tick tie-breaker,
     * not a priority queue/preemption system.
     */
    @Builder.Default
    private int priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private int maxRetries = 3;

    @Builder.Default
    private long retryDelayMs = 30_000L;

    @Builder.Default
    private double backoffMultiplier = 2.0;

    @Builder.Default
    private boolean exponentialBackoff = true;

    /** Ties this job back to whatever HTTP request created it, for cross-service log correlation. */
    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** When this job (or its next recurrence) should fire next. Null once a job reaches a terminal state. */
    private Instant nextFireAt;

    private Instant startedAt;

    private Instant completedAt;

    /** Optional - a job that hasn't fired (or, for recurring jobs, won't fire again) by this time is marked EXPIRED instead. */
    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    /** Optimistic locking - the poller and a concurrent REST pause/cancel/etc call must not silently clobber each other. */
    @Version
    private Long version;
}
