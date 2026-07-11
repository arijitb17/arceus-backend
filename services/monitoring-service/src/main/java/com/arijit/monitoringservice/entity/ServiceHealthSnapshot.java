package com.arijit.monitoringservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row per health check {@link com.arijit.monitoringservice.service.ServicePollingService}
 * performs against one monitored service (see the platform's Health Checks
 * spec: Service Status, Database Status, Redis Status, RabbitMQ Status,
 * gRPC Connectivity, API Availability, Response Time, Uptime - every field
 * below maps to one of those). Rows accumulate to form each service's
 * health history; {@link com.arijit.monitoringservice.service.LatestStateCache}
 * separately holds just the newest one per service for fast dashboard reads
 * so GET /services doesn't have to do a MAX(checkedAt) query per service on
 * every request.
 */
@Entity
@Table(name = "service_health_snapshots",
        indexes = {
                @Index(name = "idx_health_service_checkedat", columnList = "serviceName,checkedAt")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentStatus databaseStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentStatus cacheStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentStatus rabbitStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentStatus grpcStatus;

    @Column(nullable = false)
    private boolean apiAvailable;

    /** Wall-clock time this health check itself took, in milliseconds. */
    private long responseTimeMs;

    /** Reported by the monitored service via process.uptime (Actuator), 0 if unavailable. */
    private long uptimeSeconds;

    @Column(length = 50)
    private String version;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 100)
    private String correlationId;

    @Column(nullable = false)
    private Instant checkedAt;
}
