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

/**
 * Retry sweep for FAILED notifications - picks up records older than the
 * backoff window that haven't hit max-attempts yet, and retries them once.
 * A record that keeps failing just stays FAILED with attempts ==
 * max-attempts, visible (and stuck, by design - no infinite retry loop)
 * via GET /notifications.
 * <p>
 * This used to be its own {@code @Scheduled(fixedDelay=...)} poll loop -
 * every service running its own cron is exactly the duplicated-logic
 * pattern the platform has scheduler-service to avoid. It's now invoked by
 * scheduler-service's HTTP_CALLBACK executor via RetryCallbackController,
 * on a recurring job RetryJobRegistrar registers once at startup - same
 * delegation deployment-service and feature-flag-service already use for
 * their own scheduled work.
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
