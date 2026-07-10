package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * POSTs the job's payload (already a JSON string, or {@code "{}"} if the
 * job has none) verbatim to {@code callbackUrl}, plus a couple of headers
 * so the receiving service's handler can identify/correlate/dedupe the
 * call without parsing the body. Any non-2xx status, timeout, or
 * connection failure is surfaced as an exception - JobDispatchService
 * treats all of those identically as "this attempt failed".
 */
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
        jobCallbackRestClient.post()
                .uri(job.getCallbackUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Job-Id", job.getId().toString())
                .header("X-Job-Name", job.getJobName())
                .header(HttpHeaders.USER_AGENT, "arceus-scheduler-service")
                .header("X-Request-Id", job.getCorrelationId() == null ? job.getId().toString() : job.getCorrelationId())
                .body(body)
                .retrieve()
                .toBodilessEntity(); // throws RestClientResponseException on any non-2xx status
    }
}
