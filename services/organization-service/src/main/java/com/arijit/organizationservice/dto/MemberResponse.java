package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.OrganizationMember;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID id, String userId, String role, Instant joinedAt) {
    public static MemberResponse from(OrganizationMember member) {
        return new MemberResponse(member.getId(), member.getUserId(), member.getRole().name(), member.getJoinedAt());
    }
}
