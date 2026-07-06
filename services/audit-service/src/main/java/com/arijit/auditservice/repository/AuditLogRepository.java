package com.arijit.auditservice.repository;

import com.arijit.auditservice.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where (:eventType is null or a.eventType = :eventType)
              and (:actorEmail is null or a.actorEmail = :actorEmail)
              and (:from is null or a.occurredAt >= :from)
              and (:to is null or a.occurredAt <= :to)
              and (:q is null or lower(a.description) like lower(concat('%', :q, '%')))
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(
            @Param("eventType") String eventType,
            @Param("actorEmail") String actorEmail,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("q") String q,
            Pageable pageable
    );
}
