package com.arijit.notificationservice.repository;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByChannelAndCode(NotificationChannel channel, String code);
    boolean existsByChannelAndCode(NotificationChannel channel, String code);

    Optional<NotificationTemplate> findByChannelAndCodeAndActiveTrue(NotificationChannel channel, String code);

    Page<NotificationTemplate> findByChannel(NotificationChannel channel, Pageable pageable);
}
