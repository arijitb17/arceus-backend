package com.arijit.authservice.security;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    private User.UserBuilder base() {
        return User.builder().email("a@b.com").name("A").password("hash")
                .emailVerified(true).phoneVerified(true);
    }

    @Test
    void mapsFullyVerifiedUser() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(
                base().roles(new HashSet<>(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN))).build()));

        UserDetails d = service.loadUserByUsername("a@b.com");

        assertThat(d.getUsername()).isEqualTo("a@b.com");
        assertThat(d.getPassword()).isEqualTo("hash");
        assertThat(d.isEnabled()).isTrue();
        assertThat(d.isAccountNonLocked()).isTrue();
        assertThat(d.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void unverifiedLocalUserIsDisabled() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(base().phoneVerified(false).build()));

        assertThat(service.loadUserByUsername("a@b.com").isEnabled()).isFalse();
    }

    @Test
    void oauthUserIsEnabledWithoutVerificationAndHasEmptyPassword() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(
                base().provider(AuthProvider.GITHUB).password(null).emailVerified(false).phoneVerified(false).build()));

        UserDetails d = service.loadUserByUsername("a@b.com");

        assertThat(d.isEnabled()).isTrue();
        assertThat(d.getPassword()).isEmpty();
    }

    @Test
    void adminDisabledAccountIsLocked() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(base().accountEnabled(false).build()));

        assertThat(service.loadUserByUsername("a@b.com").isAccountNonLocked()).isFalse();
    }

    @Test
    void unknownEmailThrows() {
        when(userRepository.findByEmail("x@y.z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("x@y.z"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("x@y.z");
    }
}
