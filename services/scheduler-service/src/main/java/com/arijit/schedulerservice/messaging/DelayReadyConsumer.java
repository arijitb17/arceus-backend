package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import com.arijit.schedulerservice.service.JobDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@link DelayNudgeMessage}s once their TTL expires -
 * "Consume Events: Delayed execution". Just asks JobDispatchService to try
 * claiming the job right now instead of waiting for the next poll tick;
 * if the job somehow isn't due yet (clock skew) or was already claimed by
 * the poller in the meantime, tryClaimAndExecuteNow is a safe no-op
 * either way.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelayReadyConsumer {

    private final JobDispatchService dispatchService;

    @RabbitListener(queues = SchedulerMessagingTopology.DELAY_READY_QUEUE)
    public void onDelayReady(DelayNudgeMessage message) {
        try {
            UUID jobId = UUID.fromString(message.jobId());
            dispatchService.tryClaimAndExecuteNow(jobId);
        } catch (IllegalArgumentException e) {
            log.error("Malformed delay-ready nudge, not a UUID: {}", message.jobId());
        }
    }
}
