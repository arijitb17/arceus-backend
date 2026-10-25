package com.arijit.schedulerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arceus.scheduler")
public record SchedulerProperties(
        Poll poll,
        Worker worker,
        Retry retry,
        Callback callback,
        Delay delay,
        Internal internal
) {
    public record Poll(
            long intervalMs,
            int batchSize
    ) {
    }

    public record Worker(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {
    }

    public record Retry(
            int maxRetries,
            long retryDelayMs,
            double backoffMultiplier,
            boolean exponentialBackoffDefault,
            long maxBackoffMs
    ) {
    }

    public record Callback(
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
    }

    public record Delay(
            boolean enabled,
            long maxDelayMs
    ) {
    }

    public record Internal(
            String apiToken
    ) {
    }
}
