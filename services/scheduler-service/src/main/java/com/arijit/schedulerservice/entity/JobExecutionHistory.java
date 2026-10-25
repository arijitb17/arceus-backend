package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
