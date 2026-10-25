package com.arijit.authservice.controller;

import com.arijit.authservice.dto.AdminUserResponse;
import com.arijit.authservice.dto.SessionPageResponse;
import com.arijit.authservice.dto.UpdateAccountStatusRequest;
import com.arijit.authservice.dto.UpdateRolesRequest;
import com.arijit.authservice.dto.UserPageResponse;
import com.arijit.authservice.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Admin-only: list, search, and manage other users' roles/status")
public class UserManagementController {
    private final UserManagementService userManagementService;

    @GetMapping
    @Operation(summary = "List/search users (paginated)")
    public ResponseEntity<UserPageResponse> listUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userManagementService.listUsers(query, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single user's admin-facing detail")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PatchMapping("/{id}/roles")
    @Operation(summary = "Replace a user's role set")
    public ResponseEntity<AdminUserResponse> updateRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRolesRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(userManagementService.updateRoles(id, request, userDetails.getUsername()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a user's account")
    public ResponseEntity<AdminUserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(userManagementService.updateStatus(id, request, userDetails.getUsername()));
    }

    @GetMapping("/{id}/sessions")
    @Operation(summary = "List a user's active sessions (one per logged-in device)")
    public ResponseEntity<SessionPageResponse> listSessions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userManagementService.listSessions(id, page, size));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @Operation(summary = "Revoke a single session - forces that one device to log in again")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        userManagementService.revokeSession(id, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions")
    @Operation(summary = "Revoke every session for this user - forces all devices to log in again")
    public ResponseEntity<Void> revokeAllSessions(@PathVariable UUID id) {
        userManagementService.revokeAllSessions(id);
        return ResponseEntity.noContent().build();
    }
}
