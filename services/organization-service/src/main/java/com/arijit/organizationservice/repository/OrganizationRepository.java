package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlugAndDeletedAtIsNull(String slug);
    boolean existsBySlug(String slug);
}
