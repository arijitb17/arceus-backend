package com.arijit.notificationservice.scheduler;

import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polling-based retry - no separate infra needed (no Kafka, no dedicated
 * delay-queue plugin). Every {@code retry-backoff-seconds}, picks up FAILED
 * records that are older than the backoff window and haven't hit
 * max-attempts yet, and retries them once. A record that keeps failing just
 * stays FAILED with attempts == max-attempts, visible (and stuck, by
 * design - no infinite retry loop) via GET /notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryScheduler {

    private final NotificationRecordRepository recordRepository;
    private final NotificationDispatchService dispatchService;

    @Value("${notification.max-attempts:5}")
    private int maxAttempts;

    @Value("${notification.retry-backoff-seconds:30}")
    private int retryBackoffSeconds;

    @Scheduled(fixedDelayString = "${notification.retry-backoff-seconds:30}000")
    @Transactional
    public void retryFailedNotifications() {
        Instant cutoff = Instant.now().minusSeconds(retryBackoffSeconds);
        List<NotificationRecord> candidates =
                recordRepository.findByStatusAndAttemptsLessThanAndUpdatedAtBefore(NotificationStatus.FAILED, maxAttempts, cutoff);

        for (NotificationRecord record : candidates) {
            log.info("Retrying notification {} (attempt {} of {})", record.getId(), record.getAttempts() + 1, maxAttempts);
            dispatchService.retry(record);
        }
    }
}
