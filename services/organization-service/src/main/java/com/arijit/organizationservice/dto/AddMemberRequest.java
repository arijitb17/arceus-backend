package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.OrgRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(@NotNull String userId, @NotNull OrgRole role) {}
