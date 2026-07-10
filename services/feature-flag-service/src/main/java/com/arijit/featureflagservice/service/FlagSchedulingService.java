package com.arijit.featureflagservice.service;

import com.arijit.arceus.grpc.scheduler.JobIdRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import com.arijit.featureflagservice.config.FeatureFlagProperties;
import com.arijit.featureflagservice.entity.FeatureFlag;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Feature flags never run their own {@code @Scheduled} sweep for
 * activateAt/deactivateAt - per the platform rule every service delegates
 * this kind of work to scheduler-service instead of rolling its own (see
 * scheduler-service's module Javadoc). When a flag is created/updated
 * with an activateAt/deactivateAt, this registers a one-time
 * HTTP_CALLBACK job there that calls back into
 * {@code POST /internal/flags/{key}/scheduled-activate} (or
 * {@code -deactivate}) at the right instant; FlagScheduleCallbackController
 * is the receiving end. The job id scheduler-service hands back is stored
 * on the flag row so it can be cancelled if the schedule changes or the
 * flag is deleted before it fires.
 * <p>
 * Every call here is best-effort: a scheduler-service outage must not
 * block a flag create/update. On failure this logs and returns null (no
 * job id stored) - the flag is simply left without a scheduled transition
 * until the caller retries the update, rather than the whole request
 * failing over an optional feature.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlagSchedulingService {

    private static final String ACTIVATE_PATH = "/scheduled-activate";
    private static final String DEACTIVATE_PATH = "/scheduled-deactivate";

    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;
    private final FeatureFlagProperties properties;

    public String scheduleActivation(FeatureFlag flag) {
        return schedule(flag, flag.getActivateAt(), ACTIVATE_PATH, "activate");
    }

    public String scheduleDeactivation(FeatureFlag flag) {
        return schedule(flag, flag.getDeactivateAt(), DEACTIVATE_PATH, "deactivate");
    }

    public void cancel(String jobId) {
        if (jobId == null || jobId.isBlank() || !properties.scheduling().enabled()) {
            return;
        }
        try {
            schedulerStub.cancelJob(JobIdRequest.newBuilder().setId(jobId).build());
        } catch (StatusRuntimeException e) {
            log.warn("Failed to cancel scheduler job {}: {}", jobId, e.getStatus());
        }
    }

    private String schedule(FeatureFlag flag, Instant fireAt, String path, String action) {
        if (fireAt == null || !properties.scheduling().enabled()) {
            return null;
        }
        long delayMs = Math.max(0, fireAt.toEpochMilli() - System.currentTimeMillis());
        String callbackUrl = properties.scheduling().callbackBaseUrl()
                + "/internal/flags/" + encode(flag.getFlagKey()) + path
                + "?environment=" + flag.getEnvironment()
                + "&token=" + encode(properties.internal().apiToken());

        try {
            var response = schedulerStub.scheduleJob(ScheduleJobRequest.newBuilder()
                    .setJobName("feature-flag-" + action + "-" + flag.getFlagKey() + "-" + flag.getEnvironment())
                    .setServiceName("feature-flag-service")
                    .setTriggerType("DELAY")
                    .setDelayMs(delayMs)
                    .setExecutionTarget("HTTP_CALLBACK")
                    .setCallbackUrl(callbackUrl)
                    .setPriority(0)
                    .setMaxRetries(3)
                    .build());
            String jobId = response.getJob().getId();
            log.info("Scheduled {} of flag {}/{} via scheduler-service job {}", action, flag.getFlagKey(), flag.getEnvironment(), jobId);
            return jobId;
        } catch (StatusRuntimeException e) {
            log.warn("Failed to schedule {} of flag {}/{}: {}", action, flag.getFlagKey(), flag.getEnvironment(), e.getStatus());
            return null;
        }
    }

    private String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}
