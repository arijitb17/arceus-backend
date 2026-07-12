package com.arijit.cacheservice.scheduler;

import com.arijit.arceus.grpc.scheduler.ListJobsRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import com.arijit.cacheservice.config.CacheProperties;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registers the recurring "cache-sweep" job with scheduler-service once, on
 * startup - see CacheSweepService's Javadoc for what it does.
 * <p>
 * scheduler-service has no "create or update" semantics for ScheduleJob -
 * calling it twice with the same name creates two separate recurring jobs.
 * So this checks ListJobs for an existing non-terminal job with the same
 * name first, and only registers a new one if none is found - safe to run
 * on every restart/redeploy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SweepJobRegistrar implements ApplicationRunner {

    private static final String JOB_NAME = "cache-sweep";

    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;
    private final CacheProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean alreadyRegistered = schedulerStub.listJobs(ListJobsRequest.newBuilder()
                            .setServiceName("cache-service")
                            .setSize(50)
                            .build())
                    .getJobsList().stream()
                    .anyMatch(job -> JOB_NAME.equals(job.getJobName()) && !"CANCELLED".equals(job.getStatus()));

            if (alreadyRegistered) {
                log.info("scheduler-service already has an active '{}' job - not registering another", JOB_NAME);
                return;
            }

            String callbackUrl = properties.internal().callbackBaseUrl() + "/internal/cache/sweep?token=" + properties.internal().apiToken();
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName(JOB_NAME)
                    .setServiceName("cache-service")
                    .setTriggerType("FIXED_DELAY")
                    .setFixedDelayMs(properties.scheduling().sweepIntervalMs())
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPayload("{}")
                    .setMaxRetries(0)
                    .build());
            log.info("Registered recurring '{}' job with scheduler-service (job id {})", JOB_NAME, response.getJob().getId());
        } catch (StatusRuntimeException e) {
            log.error("Could not register '{}' job with scheduler-service - stale NO_EXPIRY cache entries will accumulate "
                    + "until this is registered (restart once scheduler-service is reachable): {}", JOB_NAME, e.getStatus());
        }
    }
}
