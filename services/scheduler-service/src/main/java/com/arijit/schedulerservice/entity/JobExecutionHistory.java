package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per execution attempt of a job - what {@code Job} itself can't
 * tell you, since it only ever reflects the *current* attempt. A daily
 * cron job running for a month produces roughly 30 rows here off a single
 * {@code Job} row. Rows are never updated after being written (append-only,
 * like AuditLog) - this is the system of record for "job history".
 */
@Entity
@Table(name = "job_execution_history", indexes = {
        @Index(name = "idx_history_job_id", columnList = "jobId"),
        @Index(name = "idx_history_started_at", columnList = "startedAt")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, length = 200, updatable = false)
    private String jobName;

    @Column(nullable = false, length = 100, updatable = false)
    private String serviceName;

    /** Which retry attempt this was - 1 for the first try, 2 for the first retry, etc. */
    @Column(nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private JobStatus resultStatus;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    @Column(updatable = false)
    private Instant completedAt;

    @Column(updatable = false)
    private Long durationMs;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String errorMessage;

    @Column(length = 64, updatable = false)
    private String correlationId;
}
