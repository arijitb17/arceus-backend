package com.arijit.authservice.dto;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;

import java.util.Set;
import java.util.UUID;

// What we hand back for GET /me - deliberately NOT the User entity itself,
// so the password field (or anything else internal) can never leak into a response.
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
