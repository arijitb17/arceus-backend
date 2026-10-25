package com.arijit.gatewayservice.health;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final ServiceHealthChecker serviceHealthChecker;

    @GetMapping("/health")
    public HealthResponse health() {
        Map<String, String> services = serviceHealthChecker.checkAll();
        boolean anyDown = services.containsValue("DOWN");

        return new HealthResponse(
                anyDown ? "DEGRADED" : "UP",
                services,
                formatUptime(Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()))
        );
    }

    private String formatUptime(Duration uptime) {
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        return "%dh %dm %ds".formatted(hours, minutes, seconds);
    }

    public record HealthResponse(String status, Map<String, String> services, String uptime) {
    }
}
