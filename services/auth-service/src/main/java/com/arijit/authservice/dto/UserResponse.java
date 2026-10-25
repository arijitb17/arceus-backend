package com.arijit.authservice.dto;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;

import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        boolean emailVerified,
        boolean phoneVerified,
        AuthProvider provider,
        Set<Role> roles
) {}
