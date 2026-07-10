package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import com.arijit.schedulerservice.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The other half of EXPIRED handling - JobOutcomeService.onSuccess catches
 * a recurring job whose *next* occurrence would be past expiresAt, but a
 * job that's never fired at all (still PENDING/SCHEDULED/PAUSED) needs
 * something to notice its expiresAt has simply passed. Runs far less often
 * than the poll loop since expiry is a much coarser-grained concern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobExpiryService {

    private final JobRepository jobRepository;
    private final JobLifecycleEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${arceus.scheduler.poll.expiry-interval-ms:60000}")
    @Transactional
    public void expireOverdueJobs() {
        Instant now = Instant.now();
        List<Job> expired = jobRepository.findExpired(now);
        for (Job job : expired) {
            job.setStatus(JobStatus.EXPIRED);
            job.setNextFireAt(null);
            job.setUpdatedAt(now);
            jobRepository.save(job);
            eventPublisher.expired(job);
            log.info("Job {} ({}) expired at {} without ever firing", job.getId(), job.getJobName(), job.getExpiresAt());
        }
    }
}
