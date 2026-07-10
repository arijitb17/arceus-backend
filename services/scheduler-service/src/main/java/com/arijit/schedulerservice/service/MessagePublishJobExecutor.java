package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publishes the job's payload as-is to {@code targetExchange}/{@code
 * targetRoutingKey} - the owning service is expected to already have a
 * queue bound there (the same pattern every service in this platform uses
 * for its own domain-event/OTP queues). "Success" here means the broker
 * accepted the publish, not that anything downstream actually consumed or
 * processed it - that's the receiving service's own concern, same as any
 * other RabbitMQ producer/consumer relationship.
 */
@Component
@RequiredArgsConstructor
public class MessagePublishJobExecutor implements JobExecutor {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public ExecutionTarget supports() {
        return ExecutionTarget.MESSAGE_PUBLISH;
    }

    @Override
    public void execute(Job job) {
        String body = job.getPayload() == null || job.getPayload().isBlank() ? "{}" : job.getPayload();
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setHeader("jobId", job.getId().toString())
                .setHeader("jobName", job.getJobName())
                .setHeader("correlationId", job.getCorrelationId())
                .build();
        // convertAndSend without a mandatory-publish confirm: RabbitTemplate
        // throws AmqpException synchronously on a connection-level failure
        // (broker unreachable, exchange doesn't exist with a default
        // exchange type mismatch, etc), which is enough for this executor's
        // purposes - full publisher-confirms tracking is a later concern.
        rabbitTemplate.send(job.getTargetExchange(), job.getTargetRoutingKey(), message);
    }
}
