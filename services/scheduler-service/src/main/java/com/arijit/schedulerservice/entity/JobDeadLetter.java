package com.arijit.schedulerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
