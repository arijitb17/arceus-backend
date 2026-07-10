package com.arijit.schedulerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code arceus.scheduler.*} tree in application.yml. Every field
 * here is a genuine platform-wide default; per-job overrides (max retries,
 * retry delay, backoff multiplier, priority, ...) live on the job itself
 * and take precedence - these are just what a job gets when it doesn't
 * specify one.
 */
@ConfigurationProperties(prefix = "arceus.scheduler")
public record SchedulerProperties(
        Poll poll,
        Worker worker,
        Retry retry,
        Callback callback,
        Delay delay,
        Internal internal
) {

    /** The one @Scheduled poll loop that is today's whole "engine" - see JobPollerService. */
    public record Poll(
            long intervalMs,
            int batchSize
    ) {
    }

    /** The bounded pool actual job dispatch runs on - see AsyncConfig. */
    public record Worker(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {
    }

    /** Defaults a job falls back to when it doesn't specify its own retry policy. */
    public record Retry(
            int maxRetries,
            long retryDelayMs,
            double backoffMultiplier,
            boolean exponentialBackoffDefault,
            long maxBackoffMs
    ) {
    }

    /** HTTP_CALLBACK execution target settings. */
    public record Callback(
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
    }

    /** The RabbitMQ dead-letter delay pattern used as a low-latency nudge for DELAY jobs. */
    public record Delay(
            boolean enabled,
            long maxDelayMs
    ) {
    }

    /** Shared-secret gRPC/internal auth - see grpc.GrpcAuthInterceptor. */
    public record Internal(
            String apiToken
    ) {
    }
}
