package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.dto.CreateJobRequest;
import com.arijit.schedulerservice.dto.JobStatsResponse;
import com.arijit.schedulerservice.dto.UpdateJobRequest;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.messaging.DelayNudgePublisher;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import com.arijit.schedulerservice.messaging.RetryRequestPublisher;
import com.arijit.schedulerservice.repository.JobDeadLetterRepository;
import com.arijit.schedulerservice.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a caller (REST controller or gRPC service impl - both are
 * thin adapters over this class, so REST/gRPC callers get identical
 * behavior) can do to a job outside of the poll loop itself: create, read,
 * update, cancel, pause, resume, and force-retry. JobPollerService and
 * JobDispatchService own the poll-driven state transitions (SCHEDULED ->
 * RUNNING -> COMPLETED/FAILED/RETRYING); this class owns everything a
 * human or another service explicitly asks for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final JobDeadLetterRepository deadLetterRepository;
    private final JobValidationService validationService;
    private final TriggerCalculator triggerCalculator;
    private final JobLifecycleEventPublisher eventPublisher;
    private final DelayNudgePublisher delayNudgePublisher;
    private final RetryRequestPublisher retryRequestPublisher;
    private final SchedulerProperties properties;
    private final ThreadPoolTaskExecutor jobTaskExecutor;

    @Transactional
    public Job create(CreateJobRequest request) {
        validationService.validateCreate(request);

        Instant now = Instant.now();
        Job job = Job.builder()
                .jobName(request.jobName())
                .serviceName(request.serviceName())
                .triggerType(request.triggerType())
                .cronExpression(request.cronExpression())
                .delayMs(request.delayMs())
                .fixedRateMs(request.fixedRateMs())
                .fixedDelayMs(request.fixedDelayMs())
                .executionTarget(request.executionTarget())
                .callbackUrl(request.callbackUrl())
                .targetExchange(request.targetExchange())
                .targetRoutingKey(request.targetRoutingKey())
                .payload(request.payload())
                .priority(request.priority())
                .status(JobStatus.SCHEDULED)
                .retryCount(0)
                .maxRetries(request.maxRetries() != null ? request.maxRetries() : properties.retry().maxRetries())
                .retryDelayMs(request.retryDelayMs() != null ? request.retryDelayMs() : properties.retry().retryDelayMs())
                .backoffMultiplier(request.backoffMultiplier() != null ? request.backoffMultiplier() : properties.retry().backoffMultiplier())
                .exponentialBackoff(request.exponentialBackoff() != null ? request.exponentialBackoff() : properties.retry().exponentialBackoffDefault())
                .correlationId(request.correlationId())
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(request.expiresAt())
                .build();

        job.setNextFireAt(triggerCalculator.firstFireAt(job));

        job = jobRepository.save(job);
        eventPublisher.scheduled(job);
        delayNudgePublisher.scheduleNudge(job);
        log.info("Created job {} ({}) for {}, next fire at {}", job.getId(), job.getJobName(), job.getServiceName(), job.getNextFireAt());
        return job;
    }

    @Transactional(readOnly = true)
    public Job getOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ApiException("No job with id " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<Job> list(String serviceName, JobStatus status, Pageable pageable) {
        boolean hasService = serviceName != null && !serviceName.isBlank();
        boolean hasStatus = status != null;
        if (hasService && hasStatus) {
            return jobRepository.findByServiceNameAndStatus(serviceName, status, pageable);
        } else if (hasService) {
            return jobRepository.findByServiceName(serviceName, pageable);
        } else if (hasStatus) {
            return jobRepository.findByStatus(status, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    /**
     * Only the fields that don't change what/when a job fundamentally is
     * (see UpdateJobRequest's javadoc). Disallowed while RUNNING - a job
     * shouldn't have its retry/backoff policy rewritten out from under an
     * attempt currently in flight.
     */
    @Transactional
    public Job update(UUID id, UpdateJobRequest request) {
        Job job = getOrThrow(id);
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new ApiException("Cannot update job " + id + " while it is RUNNING", HttpStatus.CONFLICT);
        }
        if (request.payload() != null) job.setPayload(request.payload());
        if (request.priority() != null) job.setPriority(request.priority());
        if (request.maxRetries() != null) job.setMaxRetries(request.maxRetries());
        if (request.retryDelayMs() != null) job.setRetryDelayMs(request.retryDelayMs());
        if (request.backoffMultiplier() != null) job.setBackoffMultiplier(request.backoffMultiplier());
        if (request.exponentialBackoff() != null) job.setExponentialBackoff(request.exponentialBackoff());
        if (request.expiresAt() != null) job.setExpiresAt(request.expiresAt());
        job.setUpdatedAt(Instant.now());
        return jobRepository.save(job);
    }

    @Transactional
    public Job cancel(UUID id) {
        Job job = getOrThrow(id);
        if (job.getStatus().isTerminal()) {
            throw new ApiException("Job " + id + " is already " + job.getStatus() + " and cannot be cancelled", HttpStatus.CONFLICT);
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setNextFireAt(null);
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);
        eventPublisher.cancelled(job);
        log.info("Cancelled job {} ({})", job.getId(), job.getJobName());
        return job;
    }

    @Transactional
    public Job pause(UUID id) {
        Job job = getOrThrow(id);
        if (job.getStatus() != JobStatus.SCHEDULED && job.getStatus() != JobStatus.RETRYING) {
            throw new ApiException("Job " + id + " must be SCHEDULED or RETRYING to pause (currently " + job.getStatus() + ")", HttpStatus.CONFLICT);
        }
        job.setStatus(JobStatus.PAUSED);
        job.setUpdatedAt(Instant.now());
        log.info("Paused job {} ({})", job.getId(), job.getJobName());
        return jobRepository.save(job);
    }

    @Transactional
    public Job resume(UUID id) {
        Job job = getOrThrow(id);
        if (job.getStatus() != JobStatus.PAUSED) {
            throw new ApiException("Job " + id + " is not PAUSED (currently " + job.getStatus() + ")", HttpStatus.CONFLICT);
        }
        // A job paused for a long time shouldn't immediately fire a
        // backlog of missed fixed-rate ticks on resume - re-anchor to the
        // trigger's natural next occurrence from now instead of whatever
        // stale nextFireAt it had when paused.
        job.setStatus(JobStatus.SCHEDULED);
        job.setNextFireAt(triggerCalculator.firstFireAt(job));
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);
        eventPublisher.scheduled(job);
        log.info("Resumed job {} ({}), next fire at {}", job.getId(), job.getJobName(), job.getNextFireAt());
        return job;
    }

    /**
     * The caller-facing entry point for POST /jobs/{id}/retry and the
     * RetryJob RPC: validates eligibility, then publishes a
     * {@code RetryRequestMessage} rather than mutating the job directly -
     * RetryRequestConsumer (or any other service that publishes the same
     * message) is what actually calls {@link #forceRetryNow} once it's
     * consumed. Returns the job in its still-FAILED state; the caller
     * should poll GET /jobs/{id} to observe the transition to SCHEDULED.
     */
    @Transactional(readOnly = true)
    public Job requestRetry(UUID id) {
        Job job = getOrThrow(id);
        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.RETRYING) {
            throw new ApiException("Job " + id + " is not FAILED/RETRYING (currently " + job.getStatus() + ") - nothing to retry", HttpStatus.CONFLICT);
        }
        retryRequestPublisher.requestRetry(id);
        log.info("Published retry request for job {} ({})", job.getId(), job.getJobName());
        return job;
    }

    /**
     * Immediate, synchronous retry request from a REST/gRPC caller who
     * wants the job requeued right now - resets nextFireAt to now without
     * waiting for the backoff delay, but does NOT reset retryCount (a
     * forced retry still counts as an attempt toward maxRetries).
     * RetryRequestConsumer calls this for messages arriving over
     * RabbitMQ, so both entry points behave identically.
     */
    @Transactional
    public Job forceRetryNow(UUID id) {
        Job job = getOrThrow(id);
        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.RETRYING) {
            throw new ApiException("Job " + id + " is not FAILED/RETRYING (currently " + job.getStatus() + ") - nothing to retry", HttpStatus.CONFLICT);
        }
        job.setStatus(JobStatus.SCHEDULED);
        job.setNextFireAt(Instant.now());
        job.setLastError(null);
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);
        eventPublisher.scheduled(job);
        log.info("Force-retrying job {} ({}) now", job.getId(), job.getJobName());
        return job;
    }

    @Transactional(readOnly = true)
    public JobStatsResponse stats() {
        Map<String, Long> counts = new HashMap<>();
        for (JobStatus status : JobStatus.values()) {
            long count = jobRepository.countByStatus(status);
            if (count > 0) {
                counts.put(status.name(), count);
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new JobStatsResponse(
                counts,
                total,
                jobTaskExecutor.getPoolSize(),
                jobTaskExecutor.getActiveCount(),
                jobTaskExecutor.getThreadPoolExecutor().getQueue().size(),
                deadLetterRepository.count()
        );
    }
}
