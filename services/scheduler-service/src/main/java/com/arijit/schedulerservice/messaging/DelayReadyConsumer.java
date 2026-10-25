package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import com.arijit.schedulerservice.service.JobDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
