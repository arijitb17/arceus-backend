package com.arijit.organizationservice.service;

import com.arijit.organizationservice.entity.OrgRole;
import com.arijit.organizationservice.entity.OrganizationMember;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Central place every service method funnels through to answer "is this
 * caller allowed to do this to this organization" - per-organization role
 * (see RoleHeaderAuthFilter's javadoc for why this can't just be a JWT
 * claim). Deliberately returns the membership row rather than a boolean so
 * callers that need the resolved OrgRole for a response (see
 * OrganizationResponse.callerRole) don't have to look it up twice.
 */
@Component
@RequiredArgsConstructor
public class OrgAccessGuard {

    private final OrganizationMemberRepository memberRepository;

    public OrganizationMember requireMember(UUID organizationId, String userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException("Not a member of this organization", HttpStatus.FORBIDDEN));
    }

    public OrganizationMember requireRole(UUID organizationId, String userId, OrgRole... allowed) {
        OrganizationMember member = requireMember(organizationId, userId);
        for (OrgRole role : allowed) {
            if (member.getRole() == role) {
                return member;
            }
        }
        throw new ApiException("Requires one of " + java.util.Arrays.toString(allowed) + " role in this organization",
                HttpStatus.FORBIDDEN);
    }

    /** OWNER or ADMIN - the two roles allowed to manage projects, modules, secrets, and (for OWNER only) membership. */
    public OrganizationMember requireManager(UUID organizationId, String userId) {
        return requireRole(organizationId, userId, OrgRole.OWNER, OrgRole.ADMIN);
    }

    /**
     * Non-throwing counterpart to {@link #requireMember}, for callers that
     * want to report "not authorized" as a normal response rather than an
     * exception - see OrganizationAccessGrpcService, which is exactly that:
     * a query, not an action, so there's nothing to roll back on failure.
     */
    public Optional<OrgRole> roleOfIfMember(UUID organizationId, String userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId).map(OrganizationMember::getRole);
    }
}
