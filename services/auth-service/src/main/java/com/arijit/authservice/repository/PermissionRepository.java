package com.arijit.authservice.repository;

import com.arijit.authservice.entity.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    boolean existsByCode(String code);
    Optional<Permission> findByCode(String code);
    Page<Permission> findByCategoryIgnoreCase(String category, Pageable pageable);
    List<Permission> findByIdIn(List<UUID> ids);
}
