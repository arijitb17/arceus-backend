package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleEnablement;
import com.arijit.organizationservice.entity.ModuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleEnablementRepository extends JpaRepository<ModuleEnablement, UUID> {
    List<ModuleEnablement> findByProjectIdAndEnvironment(UUID projectId, EnvironmentType environment);
    Optional<ModuleEnablement> findByProjectIdAndEnvironmentAndModule(UUID projectId, EnvironmentType environment, ModuleType module);
}
