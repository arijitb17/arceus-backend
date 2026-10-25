package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.Permission;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.repository.PermissionRepository;
import com.arijit.authservice.repository.RoleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock PermissionRepository permissionRepository;
    @Mock RoleDefinitionRepository roleDefinitionRepository;
    @InjectMocks RbacService service;

    private Permission perm(String code) {
        return Permission.builder().id(UUID.randomUUID()).code(code).category("users").description("d").build();
    }

    private RoleDefinition role(Role key, Permission... perms) {
        return RoleDefinition.builder().id(UUID.randomUUID()).roleKey(key).name(key.name())
                .permissions(new HashSet<>(Arrays.asList(perms))).build();
    }

    private void assertApi(Runnable r, HttpStatus s) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(s));
    }

    @Test
    void listPermissionsWithoutCategoryUsesFindAllAndClampsPaging() {
        when(permissionRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(perm("users:read"))));

        PermissionPageResponse r = service.listPermissions(null, -1, 500);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(permissionRepository).findAll(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        assertThat(cap.getValue().getSort().getOrderFor("category")).isNotNull();
        assertThat(cap.getValue().getSort().getOrderFor("code")).isNotNull();
        assertThat(r.content()).extracting(PermissionResponse::code).containsExactly("users:read");
    }

    @Test
    void listPermissionsBlankCategoryTreatedAsNoFilter() {
        when(permissionRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listPermissions("  ", 0, 10);

        verify(permissionRepository, never()).findByCategoryIgnoreCase(any(), any());
    }

    @Test
    void listPermissionsFiltersByCategory() {
        when(permissionRepository.findByCategoryIgnoreCase(eq("users"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(perm("users:write"))));

        assertThat(service.listPermissions("users", 0, 10).content()).hasSize(1);
        verify(permissionRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getPermissionFoundAndMissing() {
        Permission p = perm("a:b");
        when(permissionRepository.findById(p.getId())).thenReturn(Optional.of(p));
        assertThat(service.getPermission(p.getId()).code()).isEqualTo("a:b");

        UUID missing = UUID.randomUUID();
        when(permissionRepository.findById(missing)).thenReturn(Optional.empty());
        assertApi(() -> service.getPermission(missing), HttpStatus.NOT_FOUND);
    }

    @Test
    void createPermissionPersists() {
        when(permissionRepository.existsByCode("users:read")).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

        PermissionResponse r = service.createPermission(new CreatePermissionRequest("users:read", "users", "Read users"));

        assertThat(r.code()).isEqualTo("users:read");
        assertThat(r.category()).isEqualTo("users");
        assertThat(r.description()).isEqualTo("Read users");
    }

    @Test
    void createPermissionDuplicateConflicts() {
        when(permissionRepository.existsByCode("users:read")).thenReturn(true);

        assertApi(() -> service.createPermission(new CreatePermissionRequest("users:read", "users", null)),
                HttpStatus.CONFLICT);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void updatePermissionChangesCategoryAndDescriptionOnly() {
        Permission p = perm("users:read");
        when(permissionRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(permissionRepository.save(p)).thenReturn(p);

        PermissionResponse r = service.updatePermission(p.getId(), new UpdatePermissionRequest("billing", "new"));

        assertThat(r.code()).isEqualTo("users:read");
        assertThat(r.category()).isEqualTo("billing");
        assertThat(r.description()).isEqualTo("new");
    }

    @Test
    void deletePermissionDetachesFromRolesBeforeDeleting() {
        Permission target = perm("x:y");
        Permission other = perm("o:p");
        RoleDefinition withTarget = role(Role.ROLE_ADMIN, target, other);
        RoleDefinition without = role(Role.ROLE_USER, other);
        when(permissionRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roleDefinitionRepository.findAllByOrderByRoleKeyAsc()).thenReturn(List.of(withTarget, without));

        service.deletePermission(target.getId());

        assertThat(withTarget.getPermissions()).containsExactly(other);
        verify(roleDefinitionRepository).save(withTarget);
        verify(roleDefinitionRepository, never()).save(without);
        verify(permissionRepository).delete(target);
    }

    @Test
    void deleteMissingPermission() {
        UUID id = UUID.randomUUID();
        when(permissionRepository.findById(id)).thenReturn(Optional.empty());

        assertApi(() -> service.deletePermission(id), HttpStatus.NOT_FOUND);
        verify(permissionRepository, never()).delete(any());
    }

    @Test
    void listRolesMapsAll() {
        when(roleDefinitionRepository.findAllByOrderByRoleKeyAsc())
                .thenReturn(List.of(role(Role.ROLE_ADMIN), role(Role.ROLE_USER)));

        assertThat(service.listRoles()).extracting(RoleDefinitionResponse::roleKey)
                .containsExactly(Role.ROLE_ADMIN, Role.ROLE_USER);
    }

    @Test
    void getRoleFoundAndMissing() {
        when(roleDefinitionRepository.findByRoleKey(Role.ROLE_ADMIN)).thenReturn(Optional.of(role(Role.ROLE_ADMIN)));
        assertThat(service.getRole(Role.ROLE_ADMIN).roleKey()).isEqualTo(Role.ROLE_ADMIN);

        when(roleDefinitionRepository.findByRoleKey(Role.ROLE_USER)).thenReturn(Optional.empty());
        assertApi(() -> service.getRole(Role.ROLE_USER), HttpStatus.NOT_FOUND);
    }

    @Test
    void updateRoleDetails() {
        RoleDefinition r = role(Role.ROLE_USER);
        when(roleDefinitionRepository.findByRoleKey(Role.ROLE_USER)).thenReturn(Optional.of(r));
        when(roleDefinitionRepository.save(r)).thenReturn(r);

        RoleDefinitionResponse resp = service.updateRoleDetails(Role.ROLE_USER, new UpdateRoleDetailsRequest("Member", "Regular"));

        assertThat(resp.name()).isEqualTo("Member");
        assertThat(resp.description()).isEqualTo("Regular");
    }

    @Test
    void updateRolePermissionsReplacesSet() {
        Permission a = perm("a:a");
        Permission b = perm("b:b");
        RoleDefinition r = role(Role.ROLE_ADMIN, perm("old:old"));
        when(roleDefinitionRepository.findByRoleKey(Role.ROLE_ADMIN)).thenReturn(Optional.of(r));
        when(permissionRepository.findByIdIn(anyList())).thenReturn(List.of(a, b));
        when(roleDefinitionRepository.save(r)).thenReturn(r);

        RoleDefinitionResponse resp = service.updateRolePermissions(Role.ROLE_ADMIN,
                new UpdateRolePermissionsRequest(Set.of(a.getId(), b.getId())));

        assertThat(resp.permissions()).extracting(PermissionResponse::code).containsExactlyInAnyOrder("a:a", "b:b");
    }

    @Test
    void updateRolePermissionsRejectsUnknownIds() {
        RoleDefinition r = role(Role.ROLE_ADMIN);
        when(roleDefinitionRepository.findByRoleKey(Role.ROLE_ADMIN)).thenReturn(Optional.of(r));
        when(permissionRepository.findByIdIn(anyList())).thenReturn(List.of());

        assertApi(() -> service.updateRolePermissions(Role.ROLE_ADMIN,
                new UpdateRolePermissionsRequest(Set.of(UUID.randomUUID()))), HttpStatus.BAD_REQUEST);
        verify(roleDefinitionRepository, never()).save(any());
    }

}
