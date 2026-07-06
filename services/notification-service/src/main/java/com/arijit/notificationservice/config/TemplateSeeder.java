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
