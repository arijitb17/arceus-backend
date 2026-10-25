package com.arijit.authservice.dto;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        boolean emailVerified,
        boolean phoneVerified,
        boolean accountEnabled,
        AuthProvider provider,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
                user.isEmailVerified(), user.isPhoneVerified(), user.isAccountEnabled(),
                user.getProvider(), user.getRoles(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
