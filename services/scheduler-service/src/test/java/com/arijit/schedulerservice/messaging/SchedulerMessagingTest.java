package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.entity.*;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.service.JobDispatchService;
import com.arijit.schedulerservice.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerMessagingTest {

    @Mock RabbitTemplate rabbit;
    @Mock JobDispatchService dispatch;
    @Mock JobService jobService;

    private SchedulerProperties props(boolean delayEnabled, long maxDelay) {
        return new SchedulerProperties(null, null, null, null, new SchedulerProperties.Delay(delayEnabled, maxDelay), null);
    }

    private Job delayJob(Long delayMs, JobTriggerType type) {
        return Job.builder().id(UUID.randomUUID()).jobName("j").serviceName("s").triggerType(type)
                .status(JobStatus.SCHEDULED).delayMs(delayMs).retryCount(1).maxRetries(3).correlationId("corr").build();
    }

    @Test
    void nudgeSentWithPerMessageTtlEqualToDelay() {
        Job job = delayJob(5000L, JobTriggerType.DELAY);

        new DelayNudgePublisher(rabbit, props(true, 60_000)).scheduleNudge(job);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> mpp = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbit).convertAndSend(eq(SchedulerMessagingTopology.INTERNAL_EXCHANGE), eq(SchedulerMessagingTopology.DELAY_QUEUE),
                body.capture(), mpp.capture());
        assertThat(body.getValue()).isEqualTo(new DelayNudgeMessage(job.getId().toString()));
        Message m = new Message(new byte[0], new MessageProperties());
        mpp.getValue().postProcessMessage(m);
        assertThat(m.getMessageProperties().getExpiration()).isEqualTo("5000");
    }

    @Test
    void nudgeSkippedWhenNotApplicable() {
        new DelayNudgePublisher(rabbit, props(false, 60_000)).scheduleNudge(delayJob(5000L, JobTriggerType.DELAY));
        new DelayNudgePublisher(rabbit, props(true, 60_000)).scheduleNudge(delayJob(5000L, JobTriggerType.CRON));
        new DelayNudgePublisher(rabbit, props(true, 60_000)).scheduleNudge(delayJob(0L, JobTriggerType.DELAY));
        new DelayNudgePublisher(rabbit, props(true, 60_000)).scheduleNudge(delayJob(null, JobTriggerType.DELAY));
        new DelayNudgePublisher(rabbit, props(true, 1_000)).scheduleNudge(delayJob(5000L, JobTriggerType.DELAY));

        verifyNoInteractions(rabbit);
    }

    @Test
    void nudgeBrokerFailureSwallowedBecausePollerIsTheSafetyNet() {
        doThrow(new AmqpException("down")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

        assertThatCode(() -> new DelayNudgePublisher(rabbit, props(true, 60_000)).scheduleNudge(delayJob(5000L, JobTriggerType.DELAY)))
                .doesNotThrowAnyException();
    }

    @Test
    void retryRequestPublisherSendsJobId() {
        UUID id = UUID.randomUUID();

        new RetryRequestPublisher(rabbit).requestRetry(id);

        verify(rabbit).convertAndSend(SchedulerMessagingTopology.INTERNAL_EXCHANGE, SchedulerMessagingTopology.RETRY_REQUEST_QUEUE,
                new RetryRequestMessage(id.toString()));
    }

    @Test
    void delayReadyConsumerDispatchesValidIdsAndIgnoresMalformedOnes() {
        DelayReadyConsumer consumer = new DelayReadyConsumer(dispatch);
        UUID id = UUID.randomUUID();

        consumer.onDelayReady(new DelayNudgeMessage(id.toString()));
        consumer.onDelayReady(new DelayNudgeMessage("garbage"));

        verify(dispatch).tryClaimAndExecuteNow(id);
        verifyNoMoreInteractions(dispatch);
    }

    @Test
    void retryRequestConsumerForcesRetryThenDispatches() {
        UUID id = UUID.randomUUID();

        new RetryRequestConsumer(jobService, dispatch).onRetryRequested(new RetryRequestMessage(id.toString()));

        var order = inOrder(jobService, dispatch);
        order.verify(jobService).forceRetryNow(id);
        order.verify(dispatch).tryClaimAndExecuteNow(id);
    }

    @Test
    void retryRequestConsumerTreatsApiExceptionAsNoOpAndMalformedAsDropped() {
        UUID id = UUID.randomUUID();
        when(jobService.forceRetryNow(id)).thenThrow(new ApiException("not retryable", HttpStatus.CONFLICT));
        RetryRequestConsumer consumer = new RetryRequestConsumer(jobService, dispatch);

        consumer.onRetryRequested(new RetryRequestMessage(id.toString()));
        consumer.onRetryRequested(new RetryRequestMessage("junk"));

        verifyNoInteractions(dispatch);
    }

    @Test
    void lifecycleEventsCarryJobSnapshot() {
        JobLifecycleEventPublisher publisher = new JobLifecycleEventPublisher(rabbit);
        ReflectionTestUtils.setField(publisher, "sourceService", "scheduler-service");
        Job job = delayJob(1L, JobTriggerType.DELAY);

        publisher.scheduled(job);
        publisher.started(job);
        publisher.completed(job);
        publisher.cancelled(job);
        publisher.expired(job);
        publisher.failed(job, "kaput");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> envs = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit, times(6)).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), keys.capture(), envs.capture());
        assertThat(keys.getAllValues()).containsExactly(ArceusEventsTopology.RK_JOB_SCHEDULED, ArceusEventsTopology.RK_JOB_STARTED,
                ArceusEventsTopology.RK_JOB_COMPLETED, ArceusEventsTopology.RK_JOB_CANCELLED,
                ArceusEventsTopology.RK_JOB_EXPIRED, ArceusEventsTopology.RK_JOB_FAILED);
        EventEnvelope failed = envs.getAllValues().get(5);
        assertThat(failed.eventType()).isEqualTo("JOB_FAILED");
        assertThat(failed.correlationId()).isEqualTo("corr");
        assertThat(failed.payload()).containsEntry("jobId", job.getId().toString()).containsEntry("ownerServiceName", "s")
                .containsEntry("status", "SCHEDULED").containsEntry("retryCount", 1).containsEntry("error", "kaput");
        assertThat(envs.getAllValues().get(0).payload()).doesNotContainKey("error");
    }

    @Test
    void lifecyclePublisherNeverThrows() {
        JobLifecycleEventPublisher publisher = new JobLifecycleEventPublisher(rabbit);
        doThrow(new AmqpException("down")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.started(delayJob(1L, JobTriggerType.DELAY))).doesNotThrowAnyException();
    }
}
