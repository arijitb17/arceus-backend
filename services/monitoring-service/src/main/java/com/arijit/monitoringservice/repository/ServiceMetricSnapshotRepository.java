package com.arijit.monitoringservice.repository;

import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ServiceMetricSnapshotRepository extends JpaRepository<ServiceMetricSnapshot, UUID> {
    Optional<ServiceMetricSnapshot> findTopByServiceNameOrderByCapturedAtDesc(String serviceName);

    Page<ServiceMetricSnapshot> findByServiceNameOrderByCapturedAtDesc(String serviceName, Pageable pageable);

    Page<ServiceMetricSnapshot> findByServiceNameAndCapturedAtBetweenOrderByCapturedAtDesc(
            String serviceName, Instant from, Instant to, Pageable pageable);

    void deleteByCapturedAtBefore(Instant cutoff);
}
