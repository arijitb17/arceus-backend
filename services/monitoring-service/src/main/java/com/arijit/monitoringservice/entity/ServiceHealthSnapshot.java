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

    private long responseTimeMs;

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
