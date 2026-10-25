package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.*;
import com.arijit.schedulerservice.featureflag.JobFlagGateService;
import com.arijit.schedulerservice.messaging.JobLifecycleEventPublisher;
import com.arijit.schedulerservice.repository.JobDeadLetterRepository;
import com.arijit.schedulerservice.repository.JobExecutionHistoryRepository;
import com.arijit.schedulerservice.repository.JobRepository;
import com.arijit.schedulerservice.config.SchedulerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutionPipelineTest {

    @Mock JobRepository jobRepo;
    @Mock JobExecutionHistoryRepository historyRepo;
    @Mock JobDeadLetterRepository deadLetterRepo;
    @Mock TriggerCalculator triggerCalculator;
    @Mock RetryPolicyService retryPolicy;
    @Mock JobLifecycleEventPublisher events;
    @Mock JobExecutorResolver resolver;
    @Mock JobExecutor executor;
    @Mock ThreadPoolTaskExecutor taskExecutor;
    @Mock JobFlagGateService flagGate;

    private Job job(JobStatus status, JobTriggerType trigger) {
        return Job.builder().id(UUID.randomUUID()).jobName("job").serviceName("svc").triggerType(trigger)
                .executionTarget(ExecutionTarget.HTTP_CALLBACK).status(status).payload("{\"a\":1}")
                .correlationId("corr").maxRetries(3).build();
    }

    // ------------------------------------------------------------------ outcomes

    @Nested
    class Outcomes {

        private JobOutcomeService service;

        @BeforeEach
        void setUp() {
            service = new JobOutcomeService(jobRepo, historyRepo, deadLetterRepo, triggerCalculator, retryPolicy, events);
        }

        @Test
        void oneShotSuccessCompletesJobAndRecordsHistory() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            j.setLastError("old");
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));

            service.onSuccess(j.getId(), 1, Instant.now().minusSeconds(2));

            assertThat(j.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(j.getNextFireAt()).isNull();
            assertThat(j.getCompletedAt()).isNotNull();
            assertThat(j.getLastError()).isNull();
            ArgumentCaptor<JobExecutionHistory> h = ArgumentCaptor.forClass(JobExecutionHistory.class);
            verify(historyRepo).save(h.capture());
            assertThat(h.getValue().getResultStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(h.getValue().getAttemptNumber()).isEqualTo(1);
            assertThat(h.getValue().getDurationMs()).isGreaterThanOrEqualTo(2000L);
            assertThat(h.getValue().getCorrelationId()).isEqualTo("corr");
            verify(events).completed(j);
            verify(jobRepo).save(j);
        }

        @Test
        void recurringSuccessReschedulesAndResetsRetries() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.FIXED_RATE);
            j.setRetryCount(2);
            Instant next = Instant.now().plusSeconds(60);
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(triggerCalculator.nextRecurringFireAt(j)).thenReturn(next);

            service.onSuccess(j.getId(), 3, Instant.now());

            assertThat(j.getStatus()).isEqualTo(JobStatus.SCHEDULED);
            assertThat(j.getNextFireAt()).isEqualTo(next);
            assertThat(j.getRetryCount()).isZero();
            verify(events).scheduled(j);
        }

        @Test
        void recurringJobPastExpiryBecomesExpired() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.CRON);
            j.setExpiresAt(Instant.now().plus(10, ChronoUnit.SECONDS));
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(triggerCalculator.nextRecurringFireAt(j)).thenReturn(Instant.now().plus(1, ChronoUnit.HOURS));

            service.onSuccess(j.getId(), 1, Instant.now());

            assertThat(j.getStatus()).isEqualTo(JobStatus.EXPIRED);
            assertThat(j.getNextFireAt()).isNull();
            verify(events).expired(j);
            verify(events, never()).scheduled(any());
        }

        @Test
        void vanishedJobIsIgnored() {
            when(jobRepo.findById(any())).thenReturn(Optional.empty());

            service.onSuccess(UUID.randomUUID(), 1, Instant.now());
            service.onFailure(UUID.randomUUID(), 1, Instant.now(), new RuntimeException("x"));

            verifyNoInteractions(historyRepo, events, deadLetterRepo);
        }

        @Test
        void failureWithRetriesLeftSchedulesRetry() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            Instant retryAt = Instant.now().plusSeconds(30);
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(retryPolicy.canRetry(j)).thenReturn(true);
            when(retryPolicy.nextDelayMs(j)).thenReturn(30_000L);
            when(triggerCalculator.nextRetryFireAt(j, 30_000L)).thenReturn(retryAt);

            service.onFailure(j.getId(), 1, Instant.now(), new IllegalStateException("callback 500"));

            assertThat(j.getStatus()).isEqualTo(JobStatus.RETRYING);
            assertThat(j.getRetryCount()).isEqualTo(1);
            assertThat(j.getNextFireAt()).isEqualTo(retryAt);
            assertThat(j.getLastError()).isEqualTo("callback 500");
            verify(events).failed(j, "callback 500");
            verifyNoInteractions(deadLetterRepo);
            ArgumentCaptor<JobExecutionHistory> h = ArgumentCaptor.forClass(JobExecutionHistory.class);
            verify(historyRepo).save(h.capture());
            assertThat(h.getValue().getResultStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(h.getValue().getErrorMessage()).isEqualTo("callback 500");
        }

        @Test
        void exhaustedRetriesFailJobAndDeadLetterIt() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(retryPolicy.canRetry(j)).thenReturn(false);

            service.onFailure(j.getId(), 4, Instant.now(), new RuntimeException());

            assertThat(j.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(j.getNextFireAt()).isNull();
            assertThat(j.getCompletedAt()).isNotNull();
            assertThat(j.getLastError()).isEqualTo("RuntimeException");
            ArgumentCaptor<JobDeadLetter> dl = ArgumentCaptor.forClass(JobDeadLetter.class);
            verify(deadLetterRepo).save(dl.capture());
            assertThat(dl.getValue().getTotalAttempts()).isEqualTo(4);
            assertThat(dl.getValue().getPayload()).isEqualTo("{\"a\":1}");
            assertThat(dl.getValue().getJobId()).isEqualTo(j.getId());
            verify(retryPolicy, never()).nextDelayMs(any());
        }

        @Test
        void veryLongErrorMessagesAreTruncated() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(retryPolicy.canRetry(j)).thenReturn(false);

            service.onFailure(j.getId(), 1, Instant.now(), new RuntimeException("x".repeat(5000)));

            assertThat(j.getLastError()).hasSize(2000);
        }
    }

    // ------------------------------------------------------------------ dispatch

    @Nested
    class Dispatch {

        private JobDispatchService service;
        private JobOutcomeService outcome;

        @BeforeEach
        void setUp() {
            outcome = mock(JobOutcomeService.class);
            service = new JobDispatchService(jobRepo, resolver, outcome, events, taskExecutor, flagGate);
        }

        private Runnable submittedTask() {
            ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
            verify(taskExecutor).execute(cap.capture());
            return cap.getValue();
        }

        @Test
        void claimDueJobsMarksRunningAndPublishes() {
            Job a = job(JobStatus.SCHEDULED, JobTriggerType.DELAY);
            Job b = job(JobStatus.RETRYING, JobTriggerType.DELAY);
            Instant now = Instant.now();
            Pageable p = PageRequest.of(0, 10);
            when(jobRepo.findDueJobs(now, p)).thenReturn(List.of(a, b));

            List<UUID> ids = service.claimDueJobs(now, p);

            assertThat(ids).containsExactly(a.getId(), b.getId());
            assertThat(a.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(a.getStartedAt()).isEqualTo(now);
            verify(jobRepo).save(a);
            verify(events).started(a);
            verify(events).started(b);
        }

        @Test
        void successfulExecutionReportsSuccessWithAttemptNumber() throws Exception {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            j.setRetryCount(2);
            j.setStartedAt(Instant.now().minusSeconds(1));
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(flagGate.isExecutionEnabledFor("svc")).thenReturn(true);
            when(resolver.resolve(ExecutionTarget.HTTP_CALLBACK)).thenReturn(executor);

            service.executeClaimed(j.getId());
            submittedTask().run();

            verify(executor).execute(j);
            verify(outcome).onSuccess(eq(j.getId()), eq(3), eq(j.getStartedAt()));
            verify(outcome, never()).onFailure(any(), anyInt(), any(), any());
        }

        @Test
        void executorExceptionReportsFailure() throws Exception {
            Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            j.setStartedAt(Instant.now());
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(flagGate.isExecutionEnabledFor(any())).thenReturn(true);
            when(resolver.resolve(any())).thenReturn(executor);
            RuntimeException boom = new RuntimeException("500");
            doThrow(boom).when(executor).execute(j);

            service.executeClaimed(j.getId());
            submittedTask().run();

            verify(outcome).onFailure(eq(j.getId()), eq(1), eq(j.getStartedAt()), eq(boom));
            verify(outcome, never()).onSuccess(any(), anyInt(), any());
        }

        @Test
        void flagDisabledSkipsExecutionButCompletesJob() {
            Job j = job(JobStatus.RUNNING, JobTriggerType.FIXED_RATE);
            when(jobRepo.findById(j.getId())).thenReturn(Optional.of(j));
            when(flagGate.isExecutionEnabledFor("svc")).thenReturn(false);

            service.executeClaimed(j.getId());
            submittedTask().run();

            verifyNoInteractions(resolver);
            verify(outcome).onSuccess(eq(j.getId()), eq(1), any());
        }

        @Test
        void claimedJobThatVanishedIsSkipped() {
            when(jobRepo.findById(any())).thenReturn(Optional.empty());

            service.executeClaimed(UUID.randomUUID());
            submittedTask().run();

            verifyNoInteractions(outcome, resolver);
        }

        @Test
        void tryClaimAndExecuteNowOnlyForDueScheduledOrRetryingJobs() {
            Job due = job(JobStatus.SCHEDULED, JobTriggerType.DELAY);
            due.setNextFireAt(Instant.now().minusSeconds(1));
            Job future = job(JobStatus.SCHEDULED, JobTriggerType.DELAY);
            future.setNextFireAt(Instant.now().plusSeconds(600));
            Job running = job(JobStatus.RUNNING, JobTriggerType.DELAY);
            running.setNextFireAt(Instant.now().minusSeconds(1));
            Job noFire = job(JobStatus.SCHEDULED, JobTriggerType.DELAY);
            when(jobRepo.findById(due.getId())).thenReturn(Optional.of(due));
            when(jobRepo.findById(future.getId())).thenReturn(Optional.of(future));
            when(jobRepo.findById(running.getId())).thenReturn(Optional.of(running));
            when(jobRepo.findById(noFire.getId())).thenReturn(Optional.of(noFire));
            when(jobRepo.findById(argThatUnknown(due, future, running, noFire))).thenReturn(Optional.empty());

            service.tryClaimAndExecuteNow(future.getId());
            service.tryClaimAndExecuteNow(running.getId());
            service.tryClaimAndExecuteNow(noFire.getId());
            service.tryClaimAndExecuteNow(UUID.randomUUID());
            verify(events, never()).started(any());

            service.tryClaimAndExecuteNow(due.getId());

            assertThat(due.getStatus()).isEqualTo(JobStatus.RUNNING);
            verify(events).started(due);
            verify(taskExecutor, times(1)).execute(any(Runnable.class));
        }

        private UUID argThatUnknown(Job... known) {
            return org.mockito.ArgumentMatchers.argThat(id -> java.util.Arrays.stream(known).noneMatch(k -> k.getId().equals(id)));
        }
    }

    // ------------------------------------------------------------------ poller / expiry / resolver

    @Test
    void pollerClaimsBatchAndDispatchesEach() {
        JobDispatchService dispatch = mock(JobDispatchService.class);
        SchedulerProperties props = new SchedulerProperties(new SchedulerProperties.Poll(1000, 25), null, null, null, null, null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(dispatch.claimDueJobs(any(Instant.class), any(Pageable.class))).thenReturn(List.of(a, b));

        new JobPollerService(dispatch, props).poll();

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(dispatch).claimDueJobs(any(Instant.class), pg.capture());
        assertThat(pg.getValue().getPageSize()).isEqualTo(25);
        verify(dispatch).executeClaimed(a);
        verify(dispatch).executeClaimed(b);
    }

    @Test
    void pollerSurvivesFailures() {
        JobDispatchService dispatch = mock(JobDispatchService.class);
        SchedulerProperties props = new SchedulerProperties(new SchedulerProperties.Poll(1000, 5), null, null, null, null, null);
        when(dispatch.claimDueJobs(any(), any())).thenThrow(new IllegalStateException("db"));

        new JobPollerService(dispatch, props).poll();

        verify(dispatch, never()).executeClaimed(any());
    }

    @Test
    void expiryServiceExpiresOverdueJobsWithoutFiring() {
        Job a = job(JobStatus.SCHEDULED, JobTriggerType.DELAY);
        a.setNextFireAt(Instant.now());
        when(jobRepo.findExpired(any(Instant.class))).thenReturn(List.of(a));

        new JobExpiryService(jobRepo, events).expireOverdueJobs();

        assertThat(a.getStatus()).isEqualTo(JobStatus.EXPIRED);
        assertThat(a.getNextFireAt()).isNull();
        verify(jobRepo).save(a);
        verify(events).expired(a);
    }

    @Test
    void resolverMapsTargetsAndRejectsUnregistered() {
        JobExecutor http = mock(JobExecutor.class);
        when(http.supports()).thenReturn(ExecutionTarget.HTTP_CALLBACK);

        JobExecutorResolver r = new JobExecutorResolver(List.of(http));

        assertThat(r.resolve(ExecutionTarget.HTTP_CALLBACK)).isSameAs(http);
        assertThatThrownBy(() -> r.resolve(ExecutionTarget.MESSAGE_PUBLISH))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MESSAGE_PUBLISH");
    }

    // ------------------------------------------------------------------ executors

    /** A RestClient whose fluent chain returns the same recording spec, so every call can be verified on one mock. */
    private RestClient.RequestBodyUriSpec recordingSpec(RestClient client) {
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        RestClient.RequestBodyUriSpec spec = mock(RestClient.RequestBodyUriSpec.class, inv -> {
            Class<?> returnType = inv.getMethod().getReturnType();
            if (returnType.isInstance(inv.getMock())) return inv.getMock();
            if (returnType == RestClient.ResponseSpec.class) return response;
            return null;
        });
        when(client.post()).thenReturn(spec);
        return spec;
    }

    @Test
    void httpCallbackExecutorPostsJsonWithJobHeaders() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec spec = recordingSpec(client);
        Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
        j.setCallbackUrl("http://svc/cb");
        HttpCallbackJobExecutor exec = new HttpCallbackJobExecutor(client);

        exec.execute(j);

        assertThat(exec.supports()).isEqualTo(ExecutionTarget.HTTP_CALLBACK);
        verify(spec).uri("http://svc/cb");
        verify(spec).contentType(MediaType.APPLICATION_JSON);
        verify(spec).header("X-Job-Id", j.getId().toString());
        verify(spec).header("X-Job-Name", "job");
        verify(spec).header("X-Request-Id", "corr");
        verify(spec).body("{\"a\":1}");
    }

    @Test
    void httpCallbackExecutorDefaultsBodyAndRequestId() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec spec = recordingSpec(client);
        Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
        j.setCallbackUrl("http://svc/cb");
        j.setPayload("  ");
        j.setCorrelationId(null);

        new HttpCallbackJobExecutor(client).execute(j);

        verify(spec).body("{}");
        verify(spec).header("X-Request-Id", j.getId().toString());
    }

    @Test
    void httpCallbackFailureBubblesUpForRetryHandling() {
        RestClient client = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
        Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
        j.setCallbackUrl("http://svc/cb");
        when(client.post().uri("http://svc/cb").contentType(any()).header(anyString(), anyString())
                .header(anyString(), anyString()).header(anyString(), anyString()).header(anyString(), anyString())
                .body(anyString()).retrieve().toBodilessEntity())
                .thenThrow(new org.springframework.web.client.RestClientException("500"));

        assertThatThrownBy(() -> new HttpCallbackJobExecutor(client).execute(j))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Test
    void messagePublishExecutorSendsJsonMessageWithHeaders() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
        j.setTargetExchange("ex");
        j.setTargetRoutingKey("rk");
        MessagePublishJobExecutor exec = new MessagePublishJobExecutor(rabbit);

        exec.execute(j);

        assertThat(exec.supports()).isEqualTo(ExecutionTarget.MESSAGE_PUBLISH);
        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("ex"), eq("rk"), cap.capture());
        Message m = cap.getValue();
        assertThat(new String(m.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(m.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat((String) m.getMessageProperties().getHeader("jobId")).isEqualTo(j.getId().toString());
        assertThat((String) m.getMessageProperties().getHeader("jobName")).isEqualTo("job");
        assertThat((String) m.getMessageProperties().getHeader("correlationId")).isEqualTo("corr");
    }

    @Test
    void messagePublishExecutorUsesEmptyObjectForBlankPayload() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Job j = job(JobStatus.RUNNING, JobTriggerType.DELAY);
        j.setPayload(null);

        new MessagePublishJobExecutor(rabbit).execute(j);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(any(), any(), cap.capture());
        assertThat(new String(cap.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{}");
    }
}