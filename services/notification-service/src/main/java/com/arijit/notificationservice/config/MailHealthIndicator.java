package com.arijit.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Replaces Spring Boot's built-in mail health check (which opens a live
 * SMTP connection to smtp.gmail.com on every /actuator/health call and
 * routinely takes 1.5-3s, tripping monitoring-service's 2000ms poll
 * timeout and falsely marking this service DOWN). This indicator does the
 * real connectivity check on its own background schedule instead, and
 * /actuator/health just reads the last result - instant, no blocking I/O.
 * Paired with management.health.mail.enabled=false in application.yml.
 */
@Component
@RequiredArgsConstructor
public class MailHealthIndicator implements HealthIndicator {

    private final JavaMailSenderImpl mailSender;

    private volatile Health cachedHealth = Health.unknown()
            .withDetail("note", "not yet checked")
            .build();

    @Scheduled(fixedDelay = 60000, initialDelay = 0)
    public void refresh() {
        try {
            mailSender.testConnection();
            cachedHealth = Health.up()
                    .withDetail("location", mailSender.getHost() + ":" + mailSender.getPort())
                    .withDetail("lastChecked", Instant.now().toString())
                    .build();
        } catch (Exception e) {
            cachedHealth = Health.down()
                    .withDetail("location", mailSender.getHost() + ":" + mailSender.getPort())
                    .withDetail("error", e.getMessage())
                    .withDetail("lastChecked", Instant.now().toString())
                    .build();
        }
    }

    @Override
    public Health health() {
        return cachedHealth;
    }
}