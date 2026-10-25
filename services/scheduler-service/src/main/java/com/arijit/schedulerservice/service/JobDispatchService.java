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

@Service
@RequiredArgsConstructor
@Slf4j
public class JobDispatchService {
    private final JobRepository jobRepository;
    private final JobExecutorResolver executorResolver;
    private final JobOutcomeService outcomeService;
    private final JobLifecycleEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor jobTaskExecutor;
    private final com.arijit.schedulerservice.featureflag.JobFlagGateService jobFlagGateService;

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

    public void executeClaimed(UUID jobId) {
        jobTaskExecutor.execute(() -> runClaimedJob(jobId));
    }

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
            return;
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

        if (!jobFlagGateService.isExecutionEnabledFor(job.getServiceName())) {
            log.info("Skipping job {} ({}) - execution disabled via feature-flag-service for service '{}'",
                    jobId, job.getJobName(), job.getServiceName());
            outcomeService.onSuccess(jobId, attemptNumber, startedAt);
            return;
        }

        try {
            executorResolver.resolve(job.getExecutionTarget()).execute(job);
            outcomeService.onSuccess(jobId, attemptNumber, startedAt);
        } catch (Exception e) {
            outcomeService.onFailure(jobId, attemptNumber, startedAt, e);
        }
    }
}

