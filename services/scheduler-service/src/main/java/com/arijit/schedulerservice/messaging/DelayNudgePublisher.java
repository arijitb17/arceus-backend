package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobTriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Fires a low-latency "this job is ready" nudge for DELAY-triggered jobs,
 * as an optimization layered on top of (not a replacement for) the
 * database poller - see SchedulerMessagingTopology's javadoc and
 * arceus.scheduler.delay.enabled in application.yml. Only called for
 * DELAY jobs whose delay is within {@code delay.max-delay-ms}, since a
 * years-long TTL message sitting in RabbitMQ memory/disk is a worse trade
 * than just letting the poller's periodic scan find it whenever it fires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelayNudgePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SchedulerProperties properties;

    public void scheduleNudge(Job job) {
        if (!properties.delay().enabled() || job.getTriggerType() != JobTriggerType.DELAY) {
            return;
        }
        long delayMs = job.getDelayMs() != null ? job.getDelayMs() : 0L;
        if (delayMs <= 0 || delayMs > properties.delay().maxDelayMs()) {
            return;
        }

        try {
            MessagePostProcessor withTtl = message -> {
                message.getMessageProperties().setExpiration(String.valueOf(delayMs));
                return message;
            };
            rabbitTemplate.convertAndSend(
                    SchedulerMessagingTopology.INTERNAL_EXCHANGE,
                    SchedulerMessagingTopology.DELAY_QUEUE,
                    new DelayNudgeMessage(job.getId().toString()),
                    withTtl
            );
        } catch (Exception e) {
            // Best-effort - the poller remains the source of truth, so a
            // failed nudge only costs some latency, never correctness.
            log.warn("Failed to schedule delay nudge for job {}: {}", job.getId(), e.getMessage());
        }
    }
}
