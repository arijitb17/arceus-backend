package com.arijit.authservice.dto;

import com.arijit.authservice.entity.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(
        @NotEmpty(message = "At least one role is required")
        Set<Role> roles
) {}
