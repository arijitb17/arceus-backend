package com.arijit.monitoringservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds the {@code arceus.monitoring.*} tree in application.yml.
 */
@ConfigurationProperties(prefix = "arceus.monitoring")
public record MonitoringProperties(
        Internal internal,
        Polling polling,
        Thresholds thresholds,
        Map<String, Target> targets
) {

    /** Shared-secret REST/gRPC auth for internal callers - see security.InternalTokenAuthFilter / grpc.GrpcAuthInterceptor. */
    public record Internal(
            String apiToken
    ) {
    }

    /** Drives ServicePollingService's fixed-delay loop - see application.yml for defaults. */
    public record Polling(
            long intervalMs,
            long httpTimeoutMs
    ) {
    }

    /**
     * Default threshold values seeded into the built-in alert rules on
     * first startup (see DefaultAlertRuleSeeder) - purely a starting point,
     * every rule remains fully editable afterwards via PUT /alerts/{id}.
     */
    public record Thresholds(
            double cpuPercent,
            double memoryPercent,
            double errorRatePercent,
            double responseTimeMs
    ) {
    }

    /**
     * One entry per monitored service, keyed by service name (e.g.
     * "auth-service") - see application.yml's {@code arceus.monitoring.targets}
     * tree. {@code baseUrl} is polled for {@code /actuator/health} and
     * {@code /actuator/metrics/*}; {@code grpcHost}/{@code grpcPort} back
     * the lightweight TCP-level gRPC connectivity check in
     * ServiceHealthCollector (a full typed Ping isn't possible here since
     * every monitored service defines its own, differently-shaped gRPC
     * service - see that class's Javadoc).
     */
    public record Target(
            String baseUrl,
            String grpcHost,
            int grpcPort
    ) {
    }
}
