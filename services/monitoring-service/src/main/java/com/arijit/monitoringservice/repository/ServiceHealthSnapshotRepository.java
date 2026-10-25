package com.arijit.monitoringservice.repository;

import com.arijit.monitoringservice.entity.ServiceHealthSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ServiceHealthSnapshotRepository extends JpaRepository<ServiceHealthSnapshot, UUID> {
    Optional<ServiceHealthSnapshot> findTopByServiceNameOrderByCheckedAtDesc(String serviceName);

    Page<ServiceHealthSnapshot> findByServiceNameOrderByCheckedAtDesc(String serviceName, Pageable pageable);

    Page<ServiceHealthSnapshot> findByServiceNameAndCheckedAtBetweenOrderByCheckedAtDesc(
            @Param("serviceName") String serviceName, Instant from, Instant to, Pageable pageable);

    void deleteByCheckedAtBefore(Instant cutoff);
}
