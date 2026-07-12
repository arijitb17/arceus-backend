package com.arijit.notificationservice.config;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Same idea as auth-service's SUPER_ADMIN bootstrap: seed the one template
 * this platform actually needs today (WELCOME, sent on UserRegisteredEvent)
 * so a fresh environment works out of the box. Additional templates get
 * added here as new events/consumers are wired up - there's no admin API
 * for templates yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        seedIfMissing(
                NotificationChannel.EMAIL,
                "WELCOME",
                "Welcome to Arceus Platform, {{name}}!",
                """
                Hi {{name}},

                Your Arceus Platform account is ready. Glad to have you on board.

                If you didn't create this account, you can ignore this email.
                """
        );

        // Deployment pipeline's "Notification sends deployment status" step
        // - one template per terminal outcome DomainEventConsumer handles.
        // Same {{variable}} set on all four so the consumer can build one
        // variables map and reuse it regardless of which template fires.
        seedIfMissing(
                NotificationChannel.EMAIL,
                "DEPLOYMENT_SUCCESS",
                "[Arceus] {{serviceName}} {{serviceVersion}} deployed to {{environment}}",
                """
                Deployment {{deploymentId}} succeeded.

                Service:     {{serviceName}}
                Version:     {{serviceVersion}}
                Environment: {{environment}}
                Status:      {{status}}
                Deployed by: {{createdBy}}
                """
        );

        seedIfMissing(
                NotificationChannel.EMAIL,
                "DEPLOYMENT_FAILURE",
                "[Arceus] {{serviceName}} {{serviceVersion}} FAILED to deploy to {{environment}}",
                """
                Deployment {{deploymentId}} failed.

                Service:     {{serviceName}}
                Version:     {{serviceVersion}}
                Environment: {{environment}}
                Status:      {{status}}
                Deployed by: {{createdBy}}
                Error:       {{error}}
                """
        );

        seedIfMissing(
                NotificationChannel.EMAIL,
                "DEPLOYMENT_ROLLED_BACK",
                "[Arceus] {{serviceName}} in {{environment}} was rolled back",
                """
                Deployment {{deploymentId}} was superseded by a rollback.

                Service:          {{serviceName}}
                Environment:      {{environment}}
                Restored version: {{restoredVersion}}
                """
        );

        seedIfMissing(
                NotificationChannel.EMAIL,
                "DEPLOYMENT_CANCELLED",
                "[Arceus] {{serviceName}} {{serviceVersion}} deployment cancelled",
                """
                Deployment {{deploymentId}} was cancelled before it ran.

                Service:     {{serviceName}}
                Version:     {{serviceVersion}}
                Environment: {{environment}}
                Cancelled by context of: {{createdBy}}
                """
        );
    }

    private void seedIfMissing(NotificationChannel channel, String code, String subject, String body) {
        if (templateRepository.existsByChannelAndCode(channel, code)) {
            return;
        }
        templateRepository.save(NotificationTemplate.builder()
                .channel(channel)
                .code(code)
                .subject(subject)
                .body(body)
                .build());
        log.info("Seeded {} template '{}'", channel, code);
    }
}
