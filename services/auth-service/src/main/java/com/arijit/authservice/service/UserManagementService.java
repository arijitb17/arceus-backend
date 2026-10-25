package com.arijit.authservice.service;

import com.arijit.authservice.dto.AdminUserResponse;
import com.arijit.authservice.dto.SessionPageResponse;
import com.arijit.authservice.dto.SessionResponse;
import com.arijit.authservice.dto.UpdateAccountStatusRequest;
import com.arijit.authservice.dto.UpdateRolesRequest;
import com.arijit.authservice.dto.UserPageResponse;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.RefreshTokenRepository;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserPageResponse listUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = (query == null || query.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query, pageable);

        return UserPageResponse.from(result.map(AdminUserResponse::from));
    }

    public AdminUserResponse getUser(UUID id) {
        return AdminUserResponse.from(findOrThrow(id));
    }

    @Transactional
    public AdminUserResponse updateRoles(UUID id, UpdateRolesRequest request, String actingAdminEmail) {
        User user = findOrThrow(id);

        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && !request.roles().contains(Role.ROLE_ADMIN)) {
            log.warn("Role update rejected: admin userId={} tried to remove their own admin role", user.getId());
            throw new ApiException("You can't remove your own admin role", HttpStatus.BAD_REQUEST);
        }

        user.setRoles(request.roles());
        log.info("User roles updated: userId={} roles={} by={}", user.getId(), request.roles(), LogMasker.email(actingAdminEmail));
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID id, UpdateAccountStatusRequest request, String actingAdminEmail) {
        User user = findOrThrow(id);

        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && !request.enabled()) {
            log.warn("Status update rejected: admin userId={} tried to disable their own account", user.getId());
            throw new ApiException("You can't disable your own account", HttpStatus.BAD_REQUEST);
        }

        user.setAccountEnabled(request.enabled());
        log.info("User account status updated: userId={} enabled={} by={}", user.getId(), request.enabled(), LogMasker.email(actingAdminEmail));
        return AdminUserResponse.from(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("User lookup failed: id={}", id);
                    return new ApiException("No user found with id " + id, HttpStatus.NOT_FOUND);
                });
    }

    public SessionPageResponse listSessions(UUID userId, int page, int size) {
        User user = findOrThrow(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<RefreshToken> result = refreshTokenRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return SessionPageResponse.from(result.map(SessionResponse::from));
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        User user = findOrThrow(userId);
        RefreshToken session = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> {
                    log.warn("Session revoke rejected: session {} not found for userId={}", sessionId, userId);
                    return new ApiException("No session " + sessionId + " for this user", HttpStatus.NOT_FOUND);
                });
        refreshTokenRepository.delete(session);
        log.info("Session revoked by admin: userId={} sessionId={}", userId, sessionId);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        User user = findOrThrow(userId);
        refreshTokenRepository.deleteByUser(user);
        log.info("All sessions revoked by admin: userId={}", userId);
    }
}
