package com.arijit.organizationservice.service;

import com.arijit.organizationservice.entity.OrgRole;
import com.arijit.organizationservice.entity.OrganizationMember;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrgAccessGuard {
    private final OrganizationMemberRepository memberRepository;

    public OrganizationMember requireMember(UUID organizationId, String userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> {
                    log.warn("Access denied: user={} is not a member of org={}", userId, organizationId);
                    return new ApiException("Not a member of this organization", HttpStatus.FORBIDDEN);
                });
    }

    public OrganizationMember requireRole(UUID organizationId, String userId, OrgRole... allowed) {
        OrganizationMember member = requireMember(organizationId, userId);
        for (OrgRole role : allowed) {
            if (member.getRole() == role) {
                return member;
            }
        }
        log.warn("Access denied: user={} has role {} in org={}, requires one of {}",
                userId, member.getRole(), organizationId, java.util.Arrays.toString(allowed));
        throw new ApiException("Requires one of " + java.util.Arrays.toString(allowed) + " role in this organization",
                HttpStatus.FORBIDDEN);
    }

    public OrganizationMember requireManager(UUID organizationId, String userId) {
        return requireRole(organizationId, userId, OrgRole.OWNER, OrgRole.ADMIN);
    }

    public Optional<OrgRole> roleOfIfMember(UUID organizationId, String userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId).map(OrganizationMember::getRole);
    }
}
