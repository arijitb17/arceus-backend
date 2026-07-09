package com.arijit.authservice.config;

import com.arijit.arceus.events.domain.UserRegisteredEvent;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The platform has no other way to ever create a ROLE_ADMIN user - there's
 * no "promote to admin" endpoint, and there shouldn't be one reachable over
 * the network without one already existing. This runs once on startup and
 * creates exactly one seed admin account from environment variables, so
 * there's always at least one way in.
 * <p>
 * Idempotent by design: if a user with {@code app.super-admin.email}
 * already exists, this does nothing - it never resets that account's
 * password or role on a later restart (that would let anyone who can set
 * environment variables on a redeploy silently take over an account that
 * may have since had its password changed intentionally).
 * <p>
 * Does nothing at all if {@code app.super-admin.email} /
 * {@code app.super-admin.password} aren't set - most environments (staging,
 * local dev after the first run) don't need this to run every time.
 */
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
            log.debug("Super admin {} already exists - bootstrap is a no-op", superAdminEmail);
            return;
        }

        User admin = userRepository.save(User.builder()
                .name(superAdminName)
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .provider(AuthProvider.LOCAL)
                .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER))
                // Bootstrapped by an operator, not self-registered - there's
                // no OTP flow to run here, so both are set true directly.
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        domainEventPublisher.publish(UserRegisteredEvent.now(admin.getId(), admin.getEmail(), admin.getName()));
        log.info("Bootstrapped super admin account for {}", superAdminEmail);
    }
}
