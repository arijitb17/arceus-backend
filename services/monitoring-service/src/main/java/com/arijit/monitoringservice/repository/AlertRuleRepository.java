package com.arijit.monitoringservice.repository;

import com.arijit.monitoringservice.entity.AlertRule;
import com.arijit.monitoringservice.entity.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByEnabledTrue();

    Page<AlertRule> findAll(Pageable pageable);

    Optional<AlertRule> findByTypeAndServiceNameIsNull(AlertType type);
}
