package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
    Optional<Project> findByOrganizationIdAndSlugAndDeletedAtIsNull(UUID organizationId, String slug);
    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
