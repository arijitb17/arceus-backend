package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCallbackJobExecutor implements JobExecutor {
    private final RestClient jobCallbackRestClient;

    @Override
    public ExecutionTarget supports() {
        return ExecutionTarget.HTTP_CALLBACK;
    }

    @Override
    public void execute(Job job) {
        String body = job.getPayload() == null || job.getPayload().isBlank() ? "{}" : job.getPayload();
        log.debug("Executing HTTP callback: jobId={} name={}", job.getId(), job.getJobName());
        jobCallbackRestClient.post()
                .uri(job.getCallbackUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Job-Id", job.getId().toString())
                .header("X-Job-Name", job.getJobName())
                .header(HttpHeaders.USER_AGENT, "arceus-scheduler-service")
                .header("X-Request-Id", job.getCorrelationId() == null ? job.getId().toString() : job.getCorrelationId())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
