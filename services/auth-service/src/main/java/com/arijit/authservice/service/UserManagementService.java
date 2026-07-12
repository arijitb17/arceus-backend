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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Backs the admin-only /api/auth/users endpoints. Kept separate from
// AuthService (which is about a user managing their OWN account) since the
// authorization model is entirely different here - every method assumes
// its caller has already been confirmed as ROLE_ADMIN by the controller's
// @PreAuthorize, not by anything in here.
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

        // Guard against an admin locking themselves (or the last admin)
        // out entirely. This is a coarse check, not full "last admin"
        // accounting across the table, but it stops the single most
        // common accidental self-lockout.
        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && !request.roles().contains(Role.ROLE_ADMIN)) {
            throw new ApiException("You can't remove your own admin role", HttpStatus.BAD_REQUEST);
        }

        user.setRoles(request.roles());
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID id, UpdateAccountStatusRequest request, String actingAdminEmail) {
        User user = findOrThrow(id);

        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && !request.enabled()) {
            throw new ApiException("You can't disable your own account", HttpStatus.BAD_REQUEST);
        }

        user.setAccountEnabled(request.enabled());
        return AdminUserResponse.from(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException("No user found with id " + id, HttpStatus.NOT_FOUND));
    }

    // ---------- Sessions (gap #3: "no session-listing/revocation endpoint") ----------

    public SessionPageResponse listSessions(UUID userId, int page, int size) {
        User user = findOrThrow(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<RefreshToken> result = refreshTokenRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return SessionPageResponse.from(result.map(SessionResponse::from));
    }

    // No self-lockout guard here on purpose - unlike roles/status, revoking a
    // session only kills the refresh token. The admin's own access token
    // (already issued, already in their browser) keeps working until it
    // naturally expires, so an admin can't accidentally lock themselves out
    // of the current request the way they could with roles/status.
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        User user = findOrThrow(userId);
        RefreshToken session = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ApiException("No session " + sessionId + " for this user", HttpStatus.NOT_FOUND));
        refreshTokenRepository.delete(session);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        User user = findOrThrow(userId);
        refreshTokenRepository.deleteByUser(user);
    }
}
