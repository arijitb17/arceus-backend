package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
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
        log.debug("Publishing job message: jobId={} name={} exchange={} routingKey={}",
                job.getId(), job.getJobName(), job.getTargetExchange(), job.getTargetRoutingKey());
        rabbitTemplate.send(job.getTargetExchange(), job.getTargetRoutingKey(), message);
    }
}
