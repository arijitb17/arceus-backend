package com.arijit.authservice.repository;

import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {
    Optional<RoleDefinition> findByRoleKey(Role roleKey);
    boolean existsByRoleKey(Role roleKey);
    List<RoleDefinition> findAllByOrderByRoleKeyAsc();
}
