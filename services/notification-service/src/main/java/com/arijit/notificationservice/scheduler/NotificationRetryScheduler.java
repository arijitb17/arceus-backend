package com.arijit.notificationservice.scheduler;

import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
