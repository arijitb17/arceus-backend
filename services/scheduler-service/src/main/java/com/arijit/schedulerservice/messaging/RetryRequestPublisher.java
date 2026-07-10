package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RetryRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void requestRetry(UUID jobId) {
        rabbitTemplate.convertAndSend(
                SchedulerMessagingTopology.INTERNAL_EXCHANGE,
                SchedulerMessagingTopology.RETRY_REQUEST_QUEUE,
                new RetryRequestMessage(jobId.toString())
        );
    }
}
