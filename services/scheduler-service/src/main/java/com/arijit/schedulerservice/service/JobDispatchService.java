package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.*;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.schedulerservice.repository.JobRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the two things JobPollerService and the delay/retry consumers both
 * need: claiming a due job (flip SCHEDULED/RETRYING -> RUNNING inside one
 * short transaction, so the row is never left in an ambiguous state) and
 * then actually running it on a worker thread. The outcome (COMPLETED/
 * FAILED/RETRYING/EXPIRED) is applied by the separately-injected
 * {@link JobOutcomeService} rather than a private method here, so its
 * {@code @Transactional} methods go through Spring's proxy instead of a
 * same-class call that would silently skip the transaction.
 * <p>
 * "Claim" and "execute" are deliberately two different transactions -
 * claiming must be fast and can't hold a row lock for as long as an
 * arbitrary HTTP callback might take, so the pessimistic lock from
 * JobRepository.findDueJobs only needs to be held for the claim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobDispatchService {

    private final JobRepository jobRepository;
    private final JobExecutorResolver executorResolver;
    private final JobOutcomeService outcomeService;
    private final JobLifecycleEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor jobTaskExecutor;

    /** Called by JobPollerService for every due job in a poll batch, in the same transaction as the pessimistic-locked read. */
    @Transactional
    public List<UUID> claimDueJobs(Instant now, Pageable pageable) {
        List<Job> due = jobRepository.findDueJobs(now, pageable);
        return due.stream().map(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(now);
            job.setUpdatedAt(now);
            jobRepository.save(job);
            eventPublisher.started(job);
            return job.getId();
        }).toList();
    }

    /** Hands an already-claimed (RUNNING) job to a worker thread. */
    public void executeClaimed(UUID jobId) {
        jobTaskExecutor.execute(() -> runClaimedJob(jobId));
    }

    /**
     * Used by RetryRequestConsumer/DelayReadyConsumer, which have already
     * reset a job to SCHEDULED but want it attempted immediately rather
     * than waiting for the next poll tick - claims it in the same
     * short transaction style as the poller, then dispatches it.
     */
    @Transactional
    public void tryClaimAndExecuteNow(UUID jobId) {
        Optional<Job> maybeJob = jobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return;
        }
        Job job = maybeJob.get();
        boolean eligible = (job.getStatus() == JobStatus.SCHEDULED || job.getStatus() == JobStatus.RETRYING)
                && job.getNextFireAt() != null && !job.getNextFireAt().isAfter(Instant.now());
        if (!eligible) {
            return; // already handled by the poller, already running, or genuinely not due yet
        }
        Instant now = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(now);
        job.setUpdatedAt(now);
        jobRepository.save(job);
        eventPublisher.started(job);
        executeClaimed(job.getId());
    }

    private void runClaimedJob(UUID jobId) {
        Optional<Job> maybeJob = jobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.warn("Claimed job {} vanished before execution", jobId);
            return;
        }
        Job job = maybeJob.get();
        int attemptNumber = job.getRetryCount() + 1;
        Instant startedAt = job.getStartedAt() != null ? job.getStartedAt() : Instant.now();

        try {
            executorResolver.resolve(job.getExecutionTarget()).execute(job);
            outcomeService.onSuccess(jobId, attemptNumber, startedAt);
        } catch (Exception e) {
            outcomeService.onFailure(jobId, attemptNumber, startedAt, e);
        }
    }
}

