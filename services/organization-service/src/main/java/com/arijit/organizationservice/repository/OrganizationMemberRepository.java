package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    List<OrganizationMember> findByOrganizationId(UUID organizationId);
    List<OrganizationMember> findByUserId(String userId);
    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, String userId);
    boolean existsByOrganizationIdAndUserId(UUID organizationId, String userId);
    long countByOrganizationId(UUID organizationId);
}
