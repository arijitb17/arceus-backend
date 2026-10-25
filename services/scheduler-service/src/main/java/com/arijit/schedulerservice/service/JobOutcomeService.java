package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobDeadLetter;
import com.arijit.schedulerservice.entity.JobExecutionHistory;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import com.arijit.schedulerservice.repository.JobDeadLetterRepository;
import com.arijit.schedulerservice.repository.JobExecutionHistoryRepository;
import com.arijit.schedulerservice.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobOutcomeService {
    private final JobRepository jobRepository;
    private final JobExecutionHistoryRepository historyRepository;
    private final JobDeadLetterRepository deadLetterRepository;
    private final TriggerCalculator triggerCalculator;
    private final RetryPolicyService retryPolicyService;
    private final JobLifecycleEventPublisher eventPublisher;

    @Transactional
    public void onSuccess(UUID jobId, int attemptNumber, Instant startedAt) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} vanished before its success outcome could be applied", jobId);
            return;
        }

        Instant completedAt = Instant.now();
        recordHistory(job, attemptNumber, JobStatus.COMPLETED, startedAt, completedAt, null);
        eventPublisher.completed(job);

        job.setLastError(null);
        job.setUpdatedAt(completedAt);

        if (job.getTriggerType().isRecurring()) {
            Instant next = triggerCalculator.nextRecurringFireAt(job);
            if (job.getExpiresAt() != null && next.isAfter(job.getExpiresAt())) {
                job.setStatus(JobStatus.EXPIRED);
                job.setNextFireAt(null);
                job.setCompletedAt(completedAt);
                eventPublisher.expired(job);
                log.info("Job {} ({}) expired - next occurrence {} is past expiresAt {}", job.getId(), job.getJobName(), next, job.getExpiresAt());
            } else {
                job.setStatus(JobStatus.SCHEDULED);
                job.setNextFireAt(next);
                job.setRetryCount(0);
                eventPublisher.scheduled(job);
            }
        } else {
            job.setStatus(JobStatus.COMPLETED);
            job.setNextFireAt(null);
            job.setCompletedAt(completedAt);
        }
        jobRepository.save(job);
        log.info("Job {} ({}) attempt {} completed", job.getId(), job.getJobName(), attemptNumber);
    }

    @Transactional
    public void onFailure(UUID jobId, int attemptNumber, Instant startedAt, Exception failure) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} vanished before its failure outcome could be applied", jobId);
            return;
        }

        Instant completedAt = Instant.now();
        String error = summarize(failure);
        recordHistory(job, attemptNumber, JobStatus.FAILED, startedAt, completedAt, error);

        job.setRetryCount(attemptNumber);
        job.setLastError(error);
        job.setUpdatedAt(completedAt);

        if (retryPolicyService.canRetry(job)) {
            long delay = retryPolicyService.nextDelayMs(job);
            job.setStatus(JobStatus.RETRYING);
            job.setNextFireAt(triggerCalculator.nextRetryFireAt(job, delay));
            eventPublisher.failed(job, error);
            log.warn("Job {} ({}) attempt {} failed, retrying in {}ms: {}", job.getId(), job.getJobName(), attemptNumber, delay, error);
        } else {
            job.setStatus(JobStatus.FAILED);
            job.setNextFireAt(null);
            job.setCompletedAt(completedAt);
            eventPublisher.failed(job, error);
            recordDeadLetter(job, attemptNumber, error);
            log.error("Job {} ({}) exhausted {} retries, moved to dead-letter: {}", job.getId(), job.getJobName(), job.getMaxRetries(), error);
        }
        jobRepository.save(job);
    }

    private void recordHistory(Job job, int attemptNumber, JobStatus resultStatus, Instant startedAt, Instant completedAt, String errorMessage) {
        historyRepository.save(JobExecutionHistory.builder()
                .jobId(job.getId())
                .jobName(job.getJobName())
                .serviceName(job.getServiceName())
                .attemptNumber(attemptNumber)
                .resultStatus(resultStatus)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(completedAt.toEpochMilli() - startedAt.toEpochMilli())
                .errorMessage(errorMessage)
                .correlationId(job.getCorrelationId())
                .build());
    }

    private void recordDeadLetter(Job job, int totalAttempts, String lastError) {
        deadLetterRepository.save(JobDeadLetter.builder()
                .jobId(job.getId())
                .jobName(job.getJobName())
                .serviceName(job.getServiceName())
                .payload(job.getPayload())
                .totalAttempts(totalAttempts)
                .lastError(lastError)
                .failedAt(Instant.now())
                .build());
    }

    private String summarize(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
