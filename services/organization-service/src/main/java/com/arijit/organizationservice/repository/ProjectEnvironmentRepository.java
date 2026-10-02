package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ProjectEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectEnvironmentRepository extends JpaRepository<ProjectEnvironment, UUID> {
    List<ProjectEnvironment> findByProjectId(UUID projectId);
    Optional<ProjectEnvironment> findByProjectIdAndEnvironment(UUID projectId, EnvironmentType environment);
}
