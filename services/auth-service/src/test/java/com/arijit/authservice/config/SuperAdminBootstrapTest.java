package com.arijit.authservice.config;

import com.arijit.arceus.events.domain.UserRegisteredEvent;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock DomainEventPublisher publisher;
    @InjectMocks SuperAdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bootstrap, "superAdminEmail", "root@arceus.io");
        ReflectionTestUtils.setField(bootstrap, "superAdminPassword", "s3cret!");
        ReflectionTestUtils.setField(bootstrap, "superAdminName", "Root");
    }

    @Test
    void skipsWhenEmailBlank() {
        ReflectionTestUtils.setField(bootstrap, "superAdminEmail", "");

        bootstrap.run();

        verifyNoInteractions(userRepository, publisher);
    }

    @Test
    void skipsWhenPasswordBlank() {
        ReflectionTestUtils.setField(bootstrap, "superAdminPassword", " ");

        bootstrap.run();

        verifyNoInteractions(userRepository);
    }

    @Test
    void isNoOpWhenAdminAlreadyExists() {
        when(userRepository.findByEmail("root@arceus.io")).thenReturn(Optional.of(User.builder().build()));

        bootstrap.run();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createsVerifiedAdminWithEncodedPasswordAndPublishesEvent() {
        when(userRepository.findByEmail("root@arceus.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret!")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        bootstrap.run();

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        User admin = cap.getValue();
        assertThat(admin.getPassword()).isEqualTo("ENC");
        assertThat(admin.getName()).isEqualTo("Root");
        assertThat(admin.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(admin.getRoles()).containsExactlyInAnyOrder(Role.ROLE_ADMIN, Role.ROLE_USER);
        assertThat(admin.isFullyVerified()).isTrue();
        verify(publisher).publish(any(UserRegisteredEvent.class));
    }
}
