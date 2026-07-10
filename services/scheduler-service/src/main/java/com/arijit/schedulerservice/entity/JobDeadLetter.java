package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per job that exhausted {@code maxRetries} - a queryable "what
 * finally gave up, and why" view, separate from {@link JobExecutionHistory}
 * (which has one row per *attempt*, not one row per *job that died*).
 * Nothing currently retries out of this table automatically; it's the
 * platform's DLQ inspection surface, analogous to a RabbitMQ .dlq queue
 * but for job-level (not message-level) failures.
 */
@Entity
@Table(name = "job_dead_letters", indexes = {
        @Index(name = "idx_dlq_job_id", columnList = "jobId"),
        @Index(name = "idx_dlq_service_name", columnList = "serviceName")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, length = 200, updatable = false)
    private String jobName;

    @Column(nullable = false, length = 100, updatable = false)
    private String serviceName;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private int totalAttempts;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant failedAt;
}
