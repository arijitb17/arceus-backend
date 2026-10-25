package com.arijit.authservice.config;

import com.arijit.arceus.events.domain.UserRegisteredEvent;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${app.super-admin.email:}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:}")
    private String superAdminPassword;

    @Value("${app.super-admin.name:Super Admin}")
    private String superAdminName;

    @Override
    public void run(String... args) {
        if (superAdminEmail.isBlank() || superAdminPassword.isBlank()) {
            log.debug("app.super-admin.email/password not set - skipping super admin bootstrap");
            return;
        }

        if (userRepository.findByEmail(superAdminEmail).isPresent()) {
            log.debug("Super admin {} already exists - bootstrap is a no-op", LogMasker.email(superAdminEmail));
            return;
        }

        User admin = userRepository.save(User.builder()
                .name(superAdminName)
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .provider(AuthProvider.LOCAL)
                .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER))
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        domainEventPublisher.publish(UserRegisteredEvent.now(admin.getId(), admin.getEmail(), admin.getName()));
        log.info("Bootstrapped super admin account for {}", LogMasker.email(superAdminEmail));
    }
}
