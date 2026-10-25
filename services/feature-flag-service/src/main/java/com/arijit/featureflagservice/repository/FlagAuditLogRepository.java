package com.arijit.featureflagservice.repository;

import com.arijit.featureflagservice.entity.FlagAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlagAuditLogRepository extends JpaRepository<FlagAuditLog, UUID> {
    Page<FlagAuditLog> findByFlagKeyOrderByTimestampDesc(String flagKey, Pageable pageable);

    Page<FlagAuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
