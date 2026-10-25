package com.arijit.schedulerservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobLifecycleEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void scheduled(Job job) {
        publish(ArceusEventsTopology.RK_JOB_SCHEDULED, "JOB_SCHEDULED", job, null);
    }

    public void started(Job job) {
        publish(ArceusEventsTopology.RK_JOB_STARTED, "JOB_STARTED", job, null);
    }

    public void completed(Job job) {
        publish(ArceusEventsTopology.RK_JOB_COMPLETED, "JOB_COMPLETED", job, null);
    }

    public void failed(Job job, String error) {
        publish(ArceusEventsTopology.RK_JOB_FAILED, "JOB_FAILED", job, error);
    }

    public void cancelled(Job job) {
        publish(ArceusEventsTopology.RK_JOB_CANCELLED, "JOB_CANCELLED", job, null);
    }

    public void expired(Job job) {
        publish(ArceusEventsTopology.RK_JOB_EXPIRED, "JOB_EXPIRED", job, null);
    }

    private void publish(String routingKey, String eventType, Job job, String error) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobId", job.getId().toString());
            payload.put("jobName", job.getJobName());
            payload.put("ownerServiceName", job.getServiceName());
            payload.put("triggerType", job.getTriggerType().name());
            payload.put("status", job.getStatus().name());
            payload.put("retryCount", job.getRetryCount());
            payload.put("maxRetries", job.getMaxRetries());
            if (error != null) {
                payload.put("error", error);
            }

            EventEnvelope envelope = EventEnvelope.of(
                    eventType,
                    Instant.now(),
                    sourceService,
                    job.getCorrelationId(),
                    null,
                    null,
                    null,
                    payload
            );

            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, routingKey, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish {} for job {}: {}", eventType, job.getId(), e.getMessage());
        }
    }
}
