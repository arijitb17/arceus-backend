package com.arijit.authservice.dto;

import com.arijit.authservice.entity.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

// Full replacement of a user's role set, not an add/remove delta - simpler
// to reason about from an admin console ("this is exactly what they have
// now") than incremental grant/revoke calls.
public record UpdateRolesRequest(
        @NotEmpty(message = "At least one role is required")
        Set<Role> roles
) {}
