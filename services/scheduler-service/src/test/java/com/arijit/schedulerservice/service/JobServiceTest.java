package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.dto.*;
import com.arijit.schedulerservice.entity.*;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.messaging.DelayNudgePublisher;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import com.arijit.schedulerservice.messaging.RetryRequestPublisher;
import com.arijit.schedulerservice.repository.JobDeadLetterRepository;
import com.arijit.schedulerservice.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock JobRepository jobRepo;
    @Mock JobDeadLetterRepository deadLetterRepo;
    @Mock JobValidationService validation;
    @Mock TriggerCalculator triggerCalculator;
    @Mock JobLifecycleEventPublisher events;
    @Mock DelayNudgePublisher nudge;
    @Mock RetryRequestPublisher retryPublisher;
    @Mock ThreadPoolTaskExecutor executor;

    private JobService service;
    private final SchedulerProperties props = new SchedulerProperties(
            new SchedulerProperties.Poll(1000, 10), new SchedulerProperties.Worker(2, 4, 10),
            new SchedulerProperties.Retry(5, 45_000L, 3.0, false, 600_000L),
            new SchedulerProperties.Callback(1000, 1000), new SchedulerProperties.Delay(true, 60_000L),
            new SchedulerProperties.Internal("t"));

    @BeforeEach
    void setUp() {
        service = new JobService(jobRepo, deadLetterRepo, validation, triggerCalculator, events, nudge, retryPublisher, props, executor);
        lenient().when(jobRepo.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Job job(JobStatus status) {
        return Job.builder().id(UUID.randomUUID()).jobName("j").serviceName("svc").triggerType(JobTriggerType.DELAY).status(status).build();
    }

    private void stubFind(Job j) {
        when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
    }

    private void assertApi(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    private CreateJobRequest request(Integer maxRetries, Long retryDelay, Double backoff, Boolean exponential) {
        return new CreateJobRequest("job", "svc", JobTriggerType.DELAY, null, 5000L, null, null, ExecutionTarget.HTTP_CALLBACK,
                "http://cb", null, null, "{}", 7, maxRetries, retryDelay, backoff, exponential, "corr", null);
    }

    @Test
    void createAppliesPropertyDefaultsWhenRetryFieldsOmitted() {
        Instant fire = Instant.now().plusSeconds(5);
        when(triggerCalculator.firstFireAt(any(Job.class))).thenReturn(fire);

        Job j = service.create(request(null, null, null, null));

        assertThat(j.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(j.getMaxRetries()).isEqualTo(5);
        assertThat(j.getRetryDelayMs()).isEqualTo(45_000L);
        assertThat(j.getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(j.isExponentialBackoff()).isFalse();
        assertThat(j.getNextFireAt()).isEqualTo(fire);
        assertThat(j.getPriority()).isEqualTo(7);
        assertThat(j.getRetryCount()).isZero();
        verify(validation).validateCreate(any());
        verify(events).scheduled(j);
        verify(nudge).scheduleNudge(j);
    }

    @Test
    void createUsesExplicitRetrySettings() {
        when(triggerCalculator.firstFireAt(any(Job.class))).thenReturn(Instant.now());

        Job j = service.create(request(1, 10L, 1.5, true));

        assertThat(j.getMaxRetries()).isEqualTo(1);
        assertThat(j.getRetryDelayMs()).isEqualTo(10L);
        assertThat(j.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(j.isExponentialBackoff()).isTrue();
        assertThat(j.getCorrelationId()).isEqualTo("corr");
    }

    @Test
    void invalidRequestStopsBeforePersisting() {
        doThrow(new ApiException("bad", HttpStatus.BAD_REQUEST)).when(validation).validateCreate(any());

        assertApi(() -> service.create(request(null, null, null, null)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(jobRepo, events, nudge);
    }

    @Test
    void getOrThrow() {
        UUID id = UUID.randomUUID();
        when(jobRepo.findById(id)).thenReturn(Optional.empty());

        assertApi(() -> service.getOrThrow(id), HttpStatus.NOT_FOUND);
    }

    @Test
    void listChoosesRepositoryQueryByFilters() {
        Pageable p = PageRequest.of(0, 5);
        when(jobRepo.findByServiceNameAndStatus(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(jobRepo.findByServiceName(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(jobRepo.findByStatus(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(jobRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list("svc", JobStatus.FAILED, p);
        service.list("svc", null, p);
        service.list("  ", JobStatus.PAUSED, p);
        service.list(null, null, p);

        verify(jobRepo).findByServiceNameAndStatus("svc", JobStatus.FAILED, p);
        verify(jobRepo).findByServiceName("svc", p);
        verify(jobRepo).findByStatus(JobStatus.PAUSED, p);
        verify(jobRepo).findAll(p);
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        Job j = job(JobStatus.SCHEDULED);
        j.setPayload("old");
        j.setPriority(1);
        stubFind(j);

        Job r = service.update(j.getId(), new UpdateJobRequest(null, 9, null, 100L, null, false, null));

        assertThat(r.getPayload()).isEqualTo("old");
        assertThat(r.getPriority()).isEqualTo(9);
        assertThat(r.getRetryDelayMs()).isEqualTo(100L);
        assertThat(r.isExponentialBackoff()).isFalse();
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void cannotUpdateRunningJob() {
        Job j = job(JobStatus.RUNNING);
        stubFind(j);

        assertApi(() -> service.update(j.getId(), new UpdateJobRequest("p", null, null, null, null, null, null)), HttpStatus.CONFLICT);
        verify(jobRepo, never()).save(any());
    }

    @Test
    void cancelActiveJobAndRejectTerminal() {
        Job active = job(JobStatus.SCHEDULED);
        active.setNextFireAt(Instant.now());
        Job done = job(JobStatus.COMPLETED);
        stubFind(active);
        stubFind(done);

        Job r = service.cancel(active.getId());

        assertThat(r.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(r.getNextFireAt()).isNull();
        verify(events).cancelled(active);
        assertApi(() -> service.cancel(done.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void pauseOnlyScheduledOrRetrying() {
        Job scheduled = job(JobStatus.SCHEDULED);
        Job retrying = job(JobStatus.RETRYING);
        Job running = job(JobStatus.RUNNING);
        stubFind(scheduled);
        stubFind(retrying);
        stubFind(running);

        assertThat(service.pause(scheduled.getId()).getStatus()).isEqualTo(JobStatus.PAUSED);
        assertThat(service.pause(retrying.getId()).getStatus()).isEqualTo(JobStatus.PAUSED);
        assertApi(() -> service.pause(running.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void resumeRecomputesNextFireAt() {
        Job paused = job(JobStatus.PAUSED);
        Instant fire = Instant.now().plusSeconds(30);
        stubFind(paused);
        when(triggerCalculator.firstFireAt(paused)).thenReturn(fire);

        Job r = service.resume(paused.getId());

        assertThat(r.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(r.getNextFireAt()).isEqualTo(fire);
        verify(events).scheduled(r);
    }

    @Test
    void resumeRequiresPaused() {
        Job j = job(JobStatus.SCHEDULED);
        stubFind(j);

        assertApi(() -> service.resume(j.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void requestRetryPublishesForFailedAndRetryingOnly() {
        Job failed = job(JobStatus.FAILED);
        Job done = job(JobStatus.COMPLETED);
        stubFind(failed);
        stubFind(done);

        assertThat(service.requestRetry(failed.getId())).isSameAs(failed);
        verify(retryPublisher).requestRetry(failed.getId());
        assertApi(() -> service.requestRetry(done.getId()), HttpStatus.CONFLICT);
        verify(retryPublisher, times(1)).requestRetry(any());
    }

    @Test
    void forceRetryNowResetsToScheduledImmediately() {
        Job failed = job(JobStatus.FAILED);
        failed.setLastError("boom");
        stubFind(failed);

        Job r = service.forceRetryNow(failed.getId());

        assertThat(r.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(r.getLastError()).isNull();
        assertThat(r.getNextFireAt()).isBeforeOrEqualTo(Instant.now());
        verify(events).scheduled(r);
    }

    @Test
    void forceRetryRejectsOtherStates() {
        Job j = job(JobStatus.PAUSED);
        stubFind(j);

        assertApi(() -> service.forceRetryNow(j.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void statsOmitsZeroCountsAndReportsWorkerPool() {
        when(jobRepo.countByStatus(any())).thenReturn(0L);
        when(jobRepo.countByStatus(JobStatus.SCHEDULED)).thenReturn(3L);
        when(jobRepo.countByStatus(JobStatus.FAILED)).thenReturn(2L);
        when(deadLetterRepo.count()).thenReturn(1L);
        ThreadPoolExecutor tpe = mock(ThreadPoolExecutor.class);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        queue.add(() -> { });
        queue.add(() -> { });
        when(tpe.getQueue()).thenReturn(queue);
        when(executor.getThreadPoolExecutor()).thenReturn(tpe);
        when(executor.getPoolSize()).thenReturn(4);
        when(executor.getActiveCount()).thenReturn(1);

        JobStatsResponse s = service.stats();

        assertThat(s.countsByStatus()).isEqualTo(Map.of("SCHEDULED", 3L, "FAILED", 2L));
        assertThat(s.totalJobs()).isEqualTo(5);
        assertThat(s.workerPoolSize()).isEqualTo(4);
        assertThat(s.workerActiveCount()).isEqualTo(1);
        assertThat(s.workerQueueSize()).isEqualTo(2);
        assertThat(s.deadLetterCount()).isEqualTo(1);
    }
}
