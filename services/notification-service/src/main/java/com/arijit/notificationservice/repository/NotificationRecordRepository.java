package com.arijit.notificationservice.repository;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, UUID> {

    @Query("""
            select n from NotificationRecord n
            where (:channel is null or n.channel = :channel)
              and (:status is null or n.status = :status)
              and (:templateCode is null or n.templateCode = :templateCode)
              and (:recipient is null or n.recipient = :recipient)
            order by n.createdAt desc
            """)
    Page<NotificationRecord> search(
            @Param("channel") NotificationChannel channel,
            @Param("status") NotificationStatus status,
            @Param("templateCode") String templateCode,
            @Param("recipient") String recipient,
            Pageable pageable
    );

    List<NotificationRecord> findByStatusAndAttemptsLessThanAndUpdatedAtBefore(
            NotificationStatus status, int maxAttempts, Instant updatedBefore
    );
}
