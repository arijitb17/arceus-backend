package com.arijit.notificationservice.scheduler;

import com.arijit.arceus.grpc.scheduler.ListJobsRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RetryJobRegistrar implements ApplicationRunner {
    private static final String JOB_NAME = "notification-retry";

    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;

    @Value("${notification.retry-backoff-seconds:30}")
    private int retryBackoffSeconds;

    @Value("${arceus.notification.internal.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${arceus.notification.internal.api-token}")
    private String internalApiToken;

    public RetryJobRegistrar(SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub) {
        this.schedulerStub = schedulerStub;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean alreadyRegistered = schedulerStub.listJobs(ListJobsRequest.newBuilder()
                            .setServiceName("notification-service")
                            .setSize(50)
                            .build())
                    .getJobsList().stream()
                    .anyMatch(job -> JOB_NAME.equals(job.getJobName()) && !"CANCELLED".equals(job.getStatus()));

            if (alreadyRegistered) {
                log.info("scheduler-service already has an active '{}' job - not registering another", JOB_NAME);
                return;
            }

            String callbackUrl = callbackBaseUrl + "/internal/notifications/retry-failed?token=" + internalApiToken;
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName(JOB_NAME)
                    .setServiceName("notification-service")
                    .setTriggerType("FIXED_DELAY")
                    .setFixedDelayMs(retryBackoffSeconds * 1000L)
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPayload("{}")
                    .setMaxRetries(0)
                    .build());
            log.info("Registered recurring '{}' job with scheduler-service (job id {})", JOB_NAME, response.getJob().getId());
        } catch (StatusRuntimeException e) {
            log.error("Could not register '{}' job with scheduler-service - notification retries will not run until this "
                    + "is registered (restart once scheduler-service is reachable): {}", JOB_NAME, e.getStatus());
        }
    }
}
