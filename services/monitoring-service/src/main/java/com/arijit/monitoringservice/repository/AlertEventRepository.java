package com.arijit.monitoringservice.repository;

import com.arijit.monitoringservice.entity.AlertEvent;
import com.arijit.monitoringservice.entity.AlertState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    Optional<AlertEvent> findByRuleIdAndServiceNameAndState(UUID ruleId, String serviceName, AlertState state);

    Page<AlertEvent> findByState(AlertState state, Pageable pageable);

    Page<AlertEvent> findByServiceName(String serviceName, Pageable pageable);

    Page<AlertEvent> findByStateAndServiceName(AlertState state, String serviceName, Pageable pageable);

    long countByState(AlertState state);

    long countByStateAndTriggeredAtAfter(AlertState state, Instant since);
}
