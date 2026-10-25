package com.arijit.authservice.config;

import com.arijit.authservice.entity.Permission;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import com.arijit.authservice.repository.PermissionRepository;
import com.arijit.authservice.repository.RoleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacSeederTest {

    @Mock PermissionRepository permissionRepository;
    @Mock RoleDefinitionRepository roleDefinitionRepository;
    @InjectMocks RbacSeeder seeder;

    @Test
    void freshDatabaseSeedsAllPermissionsAndBothRoles() {
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> {
            Permission p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(roleDefinitionRepository.existsByRoleKey(any())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Permission> perms = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, atLeast(10)).save(perms.capture());
        List<String> codes = perms.getAllValues().stream().map(Permission::getCode).toList();
        assertThat(codes).contains("users:read", "users:write", "roles:manage", "audit:read", "cache:manage");
        assertThat(codes).doesNotHaveDuplicates();

        ArgumentCaptor<RoleDefinition> roles = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleDefinitionRepository, times(2)).save(roles.capture());
        RoleDefinition admin = roles.getAllValues().stream().filter(r -> r.getRoleKey() == Role.ROLE_ADMIN).findFirst().orElseThrow();
        RoleDefinition user = roles.getAllValues().stream().filter(r -> r.getRoleKey() == Role.ROLE_USER).findFirst().orElseThrow();
        assertThat(admin.getPermissions()).hasSameSizeAs(codes);
        assertThat(user.getPermissions()).isEmpty();
    }

    @Test
    void existingDataIsLeftUntouched() {
        when(permissionRepository.findByCode(anyString())).thenAnswer(i ->
                Optional.of(Permission.builder().id(UUID.randomUUID()).code(i.getArgument(0)).category("c").build()));
        when(roleDefinitionRepository.existsByRoleKey(any())).thenReturn(true);

        seeder.run();

        verify(permissionRepository, never()).save(any());
        verify(roleDefinitionRepository, never()).save(any());
    }

    @Test
    void existingPermissionsStillAttachedToNewlySeededAdminRole() {
        when(permissionRepository.findByCode(anyString())).thenAnswer(i ->
                Optional.of(Permission.builder().id(UUID.randomUUID()).code(i.getArgument(0)).category("c").build()));
        when(roleDefinitionRepository.existsByRoleKey(Role.ROLE_ADMIN)).thenReturn(false);
        when(roleDefinitionRepository.existsByRoleKey(Role.ROLE_USER)).thenReturn(true);

        seeder.run();

        ArgumentCaptor<RoleDefinition> cap = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleDefinitionRepository).save(cap.capture());
        assertThat(cap.getValue().getRoleKey()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(cap.getValue().getPermissions()).isNotEmpty();
    }
}
