package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.Secret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecretRepository extends JpaRepository<Secret, UUID> {
    List<Secret> findByProjectIdAndEnvironmentAndRevokedFalse(UUID projectId, EnvironmentType environment);
    Optional<Secret> findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(UUID projectId, EnvironmentType environment, String secretKey);
}
