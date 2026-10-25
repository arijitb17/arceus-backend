package com.arijit.monitoringservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "arceus.monitoring")
public record MonitoringProperties(
        Internal internal,
        Polling polling,
        Thresholds thresholds,
        Map<String, Target> targets
) {
    public record Internal(
            String apiToken
    ) {
    }

    public record Polling(
            long intervalMs,
            long httpTimeoutMs
    ) {
    }

    public record Thresholds(
            double cpuPercent,
            double memoryPercent,
            double errorRatePercent,
            double responseTimeMs
    ) {
    }

    public record Target(
            String baseUrl,
            String grpcHost,
            int grpcPort
    ) {
    }
}
