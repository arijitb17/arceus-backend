package com.arijit.notificationservice.scheduler;

import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @Mock NotificationRecordRepository repo;
    @Mock NotificationDispatchService dispatch;
    @InjectMocks NotificationRetryScheduler scheduler;

    @Test
    void retriesEveryFailedCandidateBelowMaxAttemptsAndPastBackoff() {
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 5);
        ReflectionTestUtils.setField(scheduler, "retryBackoffSeconds", 30);
        NotificationRecord a = NotificationRecord.builder().build();
        NotificationRecord b = NotificationRecord.builder().build();
        when(repo.findByStatusAndAttemptsLessThanAndUpdatedAtBefore(eq(NotificationStatus.FAILED), eq(5), any(Instant.class)))
                .thenReturn(List.of(a, b));
        Instant before = Instant.now();

        scheduler.retryFailedNotifications();

        verify(dispatch).retry(a);
        verify(dispatch).retry(b);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repo).findByStatusAndAttemptsLessThanAndUpdatedAtBefore(any(), eq(5), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(before.minusSeconds(29)).isAfter(before.minusSeconds(40));
    }

    @Test
    void noCandidatesNoRetries() {
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 3);
        ReflectionTestUtils.setField(scheduler, "retryBackoffSeconds", 10);
        when(repo.findByStatusAndAttemptsLessThanAndUpdatedAtBefore(any(), anyInt(), any())).thenReturn(List.of());

        scheduler.retryFailedNotifications();

        verifyNoInteractions(dispatch);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
