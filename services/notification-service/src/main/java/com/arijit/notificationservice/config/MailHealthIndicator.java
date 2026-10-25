package com.arijit.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
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
            log.warn("SMTP health check failed for {}:{}: {}", mailSender.getHost(), mailSender.getPort(), e.toString());
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