package com.arijit.organizationservice.service;

import com.arijit.organizationservice.dto.AddMemberRequest;
import com.arijit.organizationservice.dto.CreateOrganizationRequest;
import com.arijit.organizationservice.entity.OrgRole;
import com.arijit.organizationservice.entity.Organization;
import com.arijit.organizationservice.entity.OrganizationMember;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.OrganizationMemberRepository;
import com.arijit.organizationservice.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrgAccessGuard accessGuard;
    private final OrganizationEventPublisher eventPublisher;

    @Transactional
    public Organization create(CreateOrganizationRequest request, String ownerUserId) {
        if (organizationRepository.existsBySlug(request.slug())) {
            log.warn("Organization create rejected: slug '{}' already exists", request.slug());
            throw new ApiException("An organization with slug '" + request.slug() + "' already exists", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .ownerUserId(ownerUserId)
                .createdAt(now)
                .updatedAt(now)
                .build());

        memberRepository.save(OrganizationMember.builder()
                .organizationId(org.getId())
                .userId(ownerUserId)
                .role(OrgRole.OWNER)
                .invitedBy(ownerUserId)
                .joinedAt(now)
                .build());

        log.info("Organization created: orgId={} slug={} owner={}", org.getId(), org.getSlug(), ownerUserId);
        eventPublisher.organizationCreated(org, ownerUserId);
        return org;
    }

    public Organization get(UUID organizationId, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        return findActiveOrThrow(organizationId);
    }

    public List<Organization> listForUser(String userId) {
        List<UUID> orgIds = memberRepository.findByUserId(userId).stream()
                .map(OrganizationMember::getOrganizationId)
                .toList();
        return organizationRepository.findAllById(orgIds).stream()
                .filter(o -> o.getDeletedAt() == null)
                .toList();
    }

    public OrgRole roleOf(UUID organizationId, String userId) {
        return accessGuard.requireMember(organizationId, userId).getRole();
    }

    @Transactional
    public void delete(UUID organizationId, String callerUserId) {
        accessGuard.requireRole(organizationId, callerUserId, OrgRole.OWNER);
        Organization org = findActiveOrThrow(organizationId);
        org.setDeletedAt(Instant.now());
        organizationRepository.save(org);
        log.info("Organization deleted: orgId={} by={}", organizationId, callerUserId);
    }

    public List<OrganizationMember> listMembers(UUID organizationId, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        return memberRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public OrganizationMember addMember(UUID organizationId, AddMemberRequest request, String callerUserId) {
        accessGuard.requireRole(organizationId, callerUserId, OrgRole.OWNER, OrgRole.ADMIN);
        findActiveOrThrow(organizationId);

        if (memberRepository.existsByOrganizationIdAndUserId(organizationId, request.userId())) {
            log.warn("Add member rejected: user={} already in org={}", request.userId(), organizationId);
            throw new ApiException("User is already a member of this organization", HttpStatus.CONFLICT);
        }
        if (request.role() == OrgRole.OWNER) {
            log.warn("Add member rejected: attempt to add user={} as OWNER of org={}", request.userId(), organizationId);
            throw new ApiException("Cannot add a member directly as OWNER - transfer ownership separately", HttpStatus.BAD_REQUEST);
        }

        OrganizationMember member = memberRepository.save(OrganizationMember.builder()
                .organizationId(organizationId)
                .userId(request.userId())
                .role(request.role())
                .invitedBy(callerUserId)
                .joinedAt(Instant.now())
                .build());

        log.info("Member added: org={} user={} role={} by={}", organizationId, request.userId(), request.role(), callerUserId);
        eventPublisher.memberAdded(organizationId, request.userId(), request.role().name(), callerUserId);
        return member;
    }

    @Transactional
    public void removeMember(UUID organizationId, String targetUserId, String callerUserId) {
        accessGuard.requireRole(organizationId, callerUserId, OrgRole.OWNER, OrgRole.ADMIN);
        OrganizationMember target = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> {
                    log.warn("Remove member rejected: user={} is not a member of org={}", targetUserId, organizationId);
                    return new ApiException("User is not a member of this organization", HttpStatus.NOT_FOUND);
                });

        if (target.getRole() == OrgRole.OWNER) {
            log.warn("Remove member rejected: user={} is OWNER of org={}", targetUserId, organizationId);
            throw new ApiException("Cannot remove the organization's OWNER - transfer ownership first", HttpStatus.BAD_REQUEST);
        }

        memberRepository.delete(target);
        log.info("Member removed: org={} user={} by={}", organizationId, targetUserId, callerUserId);
        eventPublisher.memberRemoved(organizationId, targetUserId, callerUserId);
    }

    private Organization findActiveOrThrow(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> {
                    log.debug("Organization lookup failed: orgId={}", organizationId);
                    return new ApiException("Organization not found", HttpStatus.NOT_FOUND);
                });
        if (org.getDeletedAt() != null) {
            log.debug("Organization lookup hit soft-deleted org: orgId={}", organizationId);
            throw new ApiException("Organization not found", HttpStatus.NOT_FOUND);
        }
        return org;
    }
}
