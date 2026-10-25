package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.Permission;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.PermissionRepository;
import com.arijit.authservice.repository.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {
    private final PermissionRepository permissionRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    public PermissionPageResponse listPermissions(String category, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "category", "code"));

        var result = (category == null || category.isBlank())
                ? permissionRepository.findAll(pageable)
                : permissionRepository.findByCategoryIgnoreCase(category, pageable);

        return PermissionPageResponse.from(result.map(PermissionResponse::from));
    }

    public PermissionResponse getPermission(UUID id) {
        return PermissionResponse.from(findPermissionOrThrow(id));
    }

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            log.warn("Permission create rejected: code '{}' already exists", request.code());
            throw new ApiException("A permission with code '" + request.code() + "' already exists", HttpStatus.CONFLICT);
        }
        Permission saved = permissionRepository.save(Permission.builder()
                .code(request.code())
                .category(request.category())
                .description(request.description())
                .build());
        log.info("Permission created: id={} code={}", saved.getId(), saved.getCode());
        return PermissionResponse.from(saved);
    }

    @Transactional
    public PermissionResponse updatePermission(UUID id, UpdatePermissionRequest request) {
        Permission permission = findPermissionOrThrow(id);
        permission.setCategory(request.category());
        permission.setDescription(request.description());
        log.info("Permission updated: id={} code={}", permission.getId(), permission.getCode());
        return PermissionResponse.from(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(UUID id) {
        Permission permission = findPermissionOrThrow(id);
        for (RoleDefinition role : roleDefinitionRepository.findAllByOrderByRoleKeyAsc()) {
            if (role.getPermissions().remove(permission)) {
                roleDefinitionRepository.save(role);
            }
        }
        permissionRepository.delete(permission);
        log.info("Permission deleted: id={} code={}", permission.getId(), permission.getCode());
    }

    private Permission findPermissionOrThrow(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("Permission lookup failed: id={}", id);
                    return new ApiException("No permission found with id " + id, HttpStatus.NOT_FOUND);
                });
    }

    public List<RoleDefinitionResponse> listRoles() {
        return roleDefinitionRepository.findAllByOrderByRoleKeyAsc().stream()
                .map(RoleDefinitionResponse::from)
                .toList();
    }

    public RoleDefinitionResponse getRole(Role roleKey) {
        return RoleDefinitionResponse.from(findRoleOrThrow(roleKey));
    }

    @Transactional
    public RoleDefinitionResponse updateRoleDetails(Role roleKey, UpdateRoleDetailsRequest request) {
        RoleDefinition role = findRoleOrThrow(roleKey);
        role.setName(request.name());
        role.setDescription(request.description());
        log.info("Role details updated: role={}", roleKey);
        return RoleDefinitionResponse.from(roleDefinitionRepository.save(role));
    }

    @Transactional
    public RoleDefinitionResponse updateRolePermissions(Role roleKey, UpdateRolePermissionsRequest request) {
        RoleDefinition role = findRoleOrThrow(roleKey);

        List<Permission> found = permissionRepository.findByIdIn(List.copyOf(request.permissionIds()));
        if (found.size() != request.permissionIds().size()) {
            log.warn("Role permission update rejected: role={} requested={} found={}",
                    roleKey, request.permissionIds().size(), found.size());
            throw new ApiException("One or more permission ids don't exist", HttpStatus.BAD_REQUEST);
        }

        role.setPermissions(new HashSet<>(found));
        log.info("Role permissions replaced: role={} permissionCount={}", roleKey, found.size());
        return RoleDefinitionResponse.from(roleDefinitionRepository.save(role));
    }

    private RoleDefinition findRoleOrThrow(Role roleKey) {
        return roleDefinitionRepository.findByRoleKey(roleKey)
                .orElseThrow(() -> {
                    log.debug("Role lookup failed: role={}", roleKey);
                    return new ApiException("No role definition for " + roleKey, HttpStatus.NOT_FOUND);
                });
    }
}
