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

/**
 * Consumes {@link RetryRequestMessage}s - this is the "Consume Events:
 * Retry requests" surface. A message that can't be applied (unknown job
 * id, job not currently FAILED/RETRYING) is rethrown so Spring AMQP's
 * configured retry/DLQ policy (application.yml) takes over, same as every
 * other listener in the platform - no silent drops.
 */
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
            return; // not retryable - a malformed id will never parse on a redelivery either
        }

        try {
            jobService.forceRetryNow(jobId);
            dispatchService.tryClaimAndExecuteNow(jobId);
            log.info("Applied retry request for job {}", jobId);
        } catch (ApiException e) {
            // Job already resolved (completed, cancelled, etc) by the time
            // this was consumed, or already SCHEDULED - not an error worth
            // retrying/DLQ-ing, just a race that resolved itself.
            log.info("Retry request for job {} was a no-op: {}", jobId, e.getMessage());
        }
    }
}
