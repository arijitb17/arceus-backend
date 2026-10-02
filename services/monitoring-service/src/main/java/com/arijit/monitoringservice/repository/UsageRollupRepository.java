package com.arijit.monitoringservice.repository;

import com.arijit.monitoringservice.entity.UsageRollup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageRollupRepository extends JpaRepository<UsageRollup, UUID> {
    Optional<UsageRollup> findByProjectIdAndEnvironmentAndRouteAndDay(UUID projectId, String environment, String route, LocalDate day);

    List<UsageRollup> findByProjectIdAndEnvironmentAndDayBetweenOrderByDayAsc(
            UUID projectId, String environment, LocalDate from, LocalDate to);
}
