package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.Permission;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.PermissionRepository;
import com.arijit.authservice.repository.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
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

// Backs the admin-only /api/auth/permissions and /api/auth/roles endpoints -
// answers gap #2 from the backend-gaps doc ("no RBAC/permission model
// beyond two hardcoded roles"). Every method here assumes the caller was
// already confirmed ROLE_ADMIN by the controller's @PreAuthorize.
//
// Scope note: this does NOT change what Spring Security checks on a
// request (still the plain Role enum via hasRole(...)) - that boundary is
// deliberately left alone since rewiring authentication itself across every
// service would be a much larger, riskier change than what "add a
// Permissions module" actually calls for. What this DOES give you is a
// real, admin-editable permission catalog and a per-role assignment table
// that any service (or the frontend) can read to drive finer-grained
// authorization decisions than "admin or not".
@Service
@RequiredArgsConstructor
public class RbacService {

    private final PermissionRepository permissionRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    // ---------- Permissions ----------

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
            throw new ApiException("A permission with code '" + request.code() + "' already exists", HttpStatus.CONFLICT);
        }
        Permission saved = permissionRepository.save(Permission.builder()
                .code(request.code())
                .category(request.category())
                .description(request.description())
                .build());
        return PermissionResponse.from(saved);
    }

    @Transactional
    public PermissionResponse updatePermission(UUID id, UpdatePermissionRequest request) {
        Permission permission = findPermissionOrThrow(id);
        permission.setCategory(request.category());
        permission.setDescription(request.description());
        return PermissionResponse.from(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(UUID id) {
        Permission permission = findPermissionOrThrow(id);
        // Detach from every role first - a ManyToMany join row referencing a
        // deleted permission would otherwise leave roles with dangling
        // references (and Hibernate would just silently drop them from the
        // in-memory set next load, which is confusing to debug).
        for (RoleDefinition role : roleDefinitionRepository.findAllByOrderByRoleKeyAsc()) {
            if (role.getPermissions().remove(permission)) {
                roleDefinitionRepository.save(role);
            }
        }
        permissionRepository.delete(permission);
    }

    private Permission findPermissionOrThrow(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ApiException("No permission found with id " + id, HttpStatus.NOT_FOUND));
    }

    // ---------- Roles ----------

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
        return RoleDefinitionResponse.from(roleDefinitionRepository.save(role));
    }

    // Replaces the whole permission set in one call (not add/remove) so the
    // frontend can drive this from a checklist UI without needing a diff.
    @Transactional
    public RoleDefinitionResponse updateRolePermissions(Role roleKey, UpdateRolePermissionsRequest request) {
        RoleDefinition role = findRoleOrThrow(roleKey);

        List<Permission> found = permissionRepository.findByIdIn(List.copyOf(request.permissionIds()));
        if (found.size() != request.permissionIds().size()) {
            throw new ApiException("One or more permission ids don't exist", HttpStatus.BAD_REQUEST);
        }

        role.setPermissions(new HashSet<>(found));
        return RoleDefinitionResponse.from(roleDefinitionRepository.save(role));
    }

    private RoleDefinition findRoleOrThrow(Role roleKey) {
        return roleDefinitionRepository.findByRoleKey(roleKey)
                .orElseThrow(() -> new ApiException("No role definition for " + roleKey, HttpStatus.NOT_FOUND));
    }
}
