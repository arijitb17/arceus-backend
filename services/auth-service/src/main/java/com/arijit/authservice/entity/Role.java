package com.arijit.authservice.entity;

// A user's permission level. Kept as a plain enum (not a full table) since
// we only need a couple of roles right now - promote this to its own entity
// later if you need dynamic/custom roles.
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
