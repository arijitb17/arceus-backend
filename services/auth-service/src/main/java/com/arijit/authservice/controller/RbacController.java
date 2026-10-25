package com.arijit.authservice.controller;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RbacController {
    private final RbacService rbacService;

    @GetMapping("/api/auth/permissions")
    @Operation(summary = "List/search permissions, optionally filtered by category (paginated)")
    public ResponseEntity<PermissionPageResponse> listPermissions(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(rbacService.listPermissions(category, page, size));
    }

    @GetMapping("/api/auth/permissions/{id}")
    @Operation(summary = "Get a single permission")
    public ResponseEntity<PermissionResponse> getPermission(@PathVariable UUID id) {
        return ResponseEntity.ok(rbacService.getPermission(id));
    }

    @PostMapping("/api/auth/permissions")
    @Operation(summary = "Create a new permission, e.g. code='deployments:trigger'")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rbacService.createPermission(request));
    }

    @PutMapping("/api/auth/permissions/{id}")
    @Operation(summary = "Update a permission's category/description (code is immutable once created)")
    public ResponseEntity<PermissionResponse> updatePermission(
            @PathVariable UUID id, @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return ResponseEntity.ok(rbacService.updatePermission(id, request));
    }

    @DeleteMapping("/api/auth/permissions/{id}")
    @Operation(summary = "Permanently delete a permission (also unassigns it from every role)")
    public ResponseEntity<Void> deletePermission(@PathVariable UUID id) {
        rbacService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/api/auth/roles")
    @Operation(summary = "List every system role and its assigned permissions")
    public ResponseEntity<List<RoleDefinitionResponse>> listRoles() {
        return ResponseEntity.ok(rbacService.listRoles());
    }

    @GetMapping("/api/auth/roles/{roleKey}")
    @Operation(summary = "Get a single role and its assigned permissions")
    public ResponseEntity<RoleDefinitionResponse> getRole(@PathVariable Role roleKey) {
        return ResponseEntity.ok(rbacService.getRole(roleKey));
    }

    @PatchMapping("/api/auth/roles/{roleKey}")
    @Operation(summary = "Update a role's display name/description")
    public ResponseEntity<RoleDefinitionResponse> updateRoleDetails(
            @PathVariable Role roleKey, @Valid @RequestBody UpdateRoleDetailsRequest request
    ) {
        return ResponseEntity.ok(rbacService.updateRoleDetails(roleKey, request));
    }

    @PutMapping("/api/auth/roles/{roleKey}/permissions")
    @Operation(summary = "Replace the full set of permissions assigned to a role")
    public ResponseEntity<RoleDefinitionResponse> updateRolePermissions(
            @PathVariable Role roleKey, @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        return ResponseEntity.ok(rbacService.updateRolePermissions(roleKey, request));
    }
}
