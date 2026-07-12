package com.arijit.authservice.scheduler;

import com.arijit.arceus.grpc.scheduler.ListJobsRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registers the recurring "auth-session-cleanup" job with scheduler-service
 * once, on startup - see SessionCleanupService's Javadoc for what it does.
 * <p>
 * scheduler-service has no "create or update" semantics for ScheduleJob -
 * calling it twice with the same name creates two separate recurring jobs.
 * So this checks ListJobs for an existing non-terminal job with the same
 * name first, and only registers a new one if none is found - safe to run
 * on every restart/redeploy.
 */
@Component
@Slf4j
public class SessionCleanupJobRegistrar implements ApplicationRunner {

    private static final String JOB_NAME = "auth-session-cleanup";

    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;

    @Value("${app.scheduling.session-cleanup-interval-ms:3600000}")
    private long intervalMs;

    @Value("${app.internal.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${app.internal.api-token}")
    private String internalApiToken;

    public SessionCleanupJobRegistrar(SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub) {
        this.schedulerStub = schedulerStub;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean alreadyRegistered = schedulerStub.listJobs(ListJobsRequest.newBuilder()
                            .setServiceName("auth-service")
                            .setSize(50)
                            .build())
                    .getJobsList().stream()
                    .anyMatch(job -> JOB_NAME.equals(job.getJobName()) && !"CANCELLED".equals(job.getStatus()));

            if (alreadyRegistered) {
                log.info("scheduler-service already has an active '{}' job - not registering another", JOB_NAME);
                return;
            }

            String callbackUrl = callbackBaseUrl + "/internal/auth/sessions/clean-expired?token=" + internalApiToken;
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName(JOB_NAME)
                    .setServiceName("auth-service")
                    .setTriggerType("FIXED_DELAY")
                    .setFixedDelayMs(intervalMs)
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPayload("{}")
                    .setMaxRetries(0)
                    .build());
            log.info("Registered recurring '{}' job with scheduler-service (job id {})", JOB_NAME, response.getJob().getId());
        } catch (StatusRuntimeException e) {
            log.error("Could not register '{}' job with scheduler-service - expired refresh tokens will accumulate until this "
                    + "is registered (restart once scheduler-service is reachable): {}", JOB_NAME, e.getStatus());
        }
    }
}
