package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

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

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobTriggerType triggerType;

    @Column(length = 100)
    private String cronExpression;

    private Long delayMs;

    private Long fixedRateMs;

    private Long fixedDelayMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionTarget executionTarget;

    @Column(length = 2000)
    private String callbackUrl;

    @Column(length = 200)
    private String targetExchange;

    @Column(length = 200)
    private String targetRoutingKey;

    @Column(columnDefinition = "TEXT")
    private String payload;

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

    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant nextFireAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    /**
     * Optimistic locking - the poller and a concurrent REST pause/cancel/etc call must not silently
     * clobber each other.
     */
    @Version
    private Long version;
}
