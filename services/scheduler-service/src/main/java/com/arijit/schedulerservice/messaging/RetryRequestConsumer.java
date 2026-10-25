package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.service.JobDispatchService;
import com.arijit.schedulerservice.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryRequestConsumer {
    private final JobService jobService;
    private final JobDispatchService dispatchService;

    @RabbitListener(queues = SchedulerMessagingTopology.RETRY_REQUEST_QUEUE)
    public void onRetryRequested(RetryRequestMessage message) {
        UUID jobId;
        try {
            jobId = UUID.fromString(message.jobId());
        } catch (IllegalArgumentException e) {
            log.error("Malformed retry request, not a UUID: {}", message.jobId());
            return;
        }

        try {
            jobService.forceRetryNow(jobId);
            dispatchService.tryClaimAndExecuteNow(jobId);
            log.info("Applied retry request for job {}", jobId);
        } catch (ApiException e) {
            log.info("Retry request for job {} was a no-op: {}", jobId, e.getMessage());
        }
    }
}
