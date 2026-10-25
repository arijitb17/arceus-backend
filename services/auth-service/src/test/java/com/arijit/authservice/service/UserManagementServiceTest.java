package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.RefreshTokenRepository;
import com.arijit.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks UserManagementService service;

    private User target;

    @BeforeEach
    void setUp() {
        target = User.builder().id(UUID.randomUUID()).name("Misty").email("misty@cerulean.city")
                .roles(new HashSet<>(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN))).build();
    }

    private void assertApi(Runnable r, HttpStatus s) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(s));
    }

    @Test
    void listUsersWithoutQueryUsesFindAllSortedByCreatedAtDesc() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(target)));

        UserPageResponse r = service.listUsers(null, -3, 1000);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        assertThat(cap.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(r.content()).extracting(AdminUserResponse::email).containsExactly("misty@cerulean.city");
    }

    @Test
    void listUsersWithQuerySearchesEmailAndName() {
        when(userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(eq("mis"), eq("mis"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(target)));

        assertThat(service.listUsers("mis", 0, 10).totalElements()).isEqualTo(1);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void blankQueryFallsBackToFindAll() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listUsers("   ", 0, 0);

        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void getUserFoundAndMissing() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        assertThat(service.getUser(target.getId()).name()).isEqualTo("Misty");

        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());
        assertApi(() -> service.getUser(missing), HttpStatus.NOT_FOUND);
    }

    @Test
    void updateRolesAppliesNewRoles() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        AdminUserResponse r = service.updateRoles(target.getId(),
                new UpdateRolesRequest(Set.of(Role.ROLE_USER)), "admin@arceus.io");

        assertThat(r.roles()).containsExactly(Role.ROLE_USER);
    }

    @Test
    void adminCannotRemoveOwnAdminRoleCaseInsensitive() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertApi(() -> service.updateRoles(target.getId(), new UpdateRolesRequest(Set.of(Role.ROLE_USER)),
                "MISTY@cerulean.city"), HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminMayEditOwnRolesWhileKeepingAdmin() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        service.updateRoles(target.getId(), new UpdateRolesRequest(Set.of(Role.ROLE_ADMIN)), target.getEmail());

        assertThat(target.getRoles()).containsExactly(Role.ROLE_ADMIN);
    }

    @Test
    void updateStatusDisablesAnotherUser() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        AdminUserResponse r = service.updateStatus(target.getId(), new UpdateAccountStatusRequest(false), "admin@arceus.io");

        assertThat(r.accountEnabled()).isFalse();
    }

    @Test
    void adminCannotDisableSelfButCanReEnable() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        assertApi(() -> service.updateStatus(target.getId(), new UpdateAccountStatusRequest(false), target.getEmail()),
                HttpStatus.BAD_REQUEST);

        when(userRepository.save(target)).thenReturn(target);
        assertThat(service.updateStatus(target.getId(), new UpdateAccountStatusRequest(true), target.getEmail())
                .accountEnabled()).isTrue();
    }

    @Test
    void listSessionsMapsTokens() {
        RefreshToken t = RefreshToken.builder().id(UUID.randomUUID()).user(target).tokenHash("h")
                .expiryDate(Instant.now().plusSeconds(100)).createdAt(Instant.now()).build();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(refreshTokenRepository.findByUserOrderByCreatedAtDesc(eq(target), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        SessionPageResponse r = service.listSessions(target.getId(), 0, 20);

        assertThat(r.content()).hasSize(1);
        assertThat(r.content().get(0).expired()).isFalse();
    }

    @Test
    void revokeSessionDeletesOrThrows() {
        UUID sid = UUID.randomUUID();
        RefreshToken session = RefreshToken.builder().id(sid).user(target).build();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(refreshTokenRepository.findByIdAndUser(sid, target)).thenReturn(Optional.of(session), Optional.empty());

        service.revokeSession(target.getId(), sid);
        verify(refreshTokenRepository).delete(session);

        assertApi(() -> service.revokeSession(target.getId(), sid), HttpStatus.NOT_FOUND);
    }

    @Test
    void revokeAllSessionsDeletesByUser() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.revokeAllSessions(target.getId());

        verify(refreshTokenRepository).deleteByUser(target);
    }

    @Test
    void revokeAllSessionsUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertApi(() -> service.revokeAllSessions(id), HttpStatus.NOT_FOUND);
        verifyNoInteractions(refreshTokenRepository);
    }
}
