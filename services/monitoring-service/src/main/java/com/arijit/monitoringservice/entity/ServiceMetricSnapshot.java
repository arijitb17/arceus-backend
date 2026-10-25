package com.arijit.monitoringservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Left {@code null} otherwise rather than defaulting to 0, so a dashboard can distinguish
 * "definitely zero" from "not instrumented yet" - this is exactly the seam that lets scheduler-
 * service/cache-service/etc add real instrumentation later without monitoring-service's public API
 * changing at all.</li> </ul>
 */
@Entity
@Table(name = "service_metric_snapshots",
        indexes = {
                @Index(name = "idx_metric_service_capturedat", columnList = "serviceName,capturedAt")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMetricSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String serviceName;

    private Long requestCount;
    private Long errorCount;
    private Double successRate;
    private Double avgResponseTimeMs;

    private Double cpuUsagePercent;
    private Double memoryUsedMb;
    private Double memoryMaxMb;
    private Double jvmHeapUsedMb;
    private Integer threadCount;

    private Integer activeConnections;
    private Integer queueSize;

    private Double cacheHitRatio;
    private Double cacheMissRatio;

    private Integer schedulerActiveJobs;
    private Integer schedulerFailedJobs;

    private Long featureFlagEvaluations;

    private Long rateLimitRequests;
    private Long rateLimitBlocked;

    @Column(nullable = false)
    private Instant capturedAt;
}
