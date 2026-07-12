package com.arijit.authservice.config;

import com.arijit.authservice.entity.Permission;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;
import com.arijit.authservice.repository.PermissionRepository;
import com.arijit.authservice.repository.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent, same pattern as {@link SuperAdminBootstrap}: seeds a starter
 * permission catalog covering the admin-portal modules the backend-gaps doc
 * lists (Users, Roles/Permissions, Sessions, Notifications, plus the other
 * Gateway-routed services), and creates exactly one {@link RoleDefinition}
 * row per {@link Role} enum value with a sensible default assignment -
 * ROLE_ADMIN gets everything, ROLE_USER gets nothing (it's the unprivileged
 * default). Everything after that first run is admin-editable via
 * RbacController, and this never overwrites an existing row - so an admin's
 * later customization survives every restart/redeploy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    // code -> [category, description]
    private static final Map<String, String[]> BASE_PERMISSIONS = new LinkedHashMap<>();
    static {
        BASE_PERMISSIONS.put("users:read", new String[]{"Users", "View other users and their profiles"});
        BASE_PERMISSIONS.put("users:write", new String[]{"Users", "Change another user's roles or enabled status"});
        BASE_PERMISSIONS.put("sessions:read", new String[]{"Sessions", "View another user's active sessions"});
        BASE_PERMISSIONS.put("sessions:revoke", new String[]{"Sessions", "Revoke another user's session(s)"});
        BASE_PERMISSIONS.put("roles:manage", new String[]{"Permissions", "Manage the permission catalog and role assignments"});
        BASE_PERMISSIONS.put("notifications:send", new String[]{"Notifications", "Send an ad-hoc notification"});
        BASE_PERMISSIONS.put("notifications:read", new String[]{"Notifications", "View notification delivery history"});
        BASE_PERMISSIONS.put("notifications:templates:manage", new String[]{"Notifications", "Create/edit/delete notification templates"});
        BASE_PERMISSIONS.put("audit:read", new String[]{"Audit", "View the audit log"});
        BASE_PERMISSIONS.put("monitoring:read", new String[]{"Monitoring", "View monitoring dashboards/metrics"});
        BASE_PERMISSIONS.put("deployments:read", new String[]{"Deployments", "View deployment history/status"});
        BASE_PERMISSIONS.put("deployments:trigger", new String[]{"Deployments", "Trigger or roll back a deployment"});
        BASE_PERMISSIONS.put("scheduler:manage", new String[]{"Scheduler", "Create/edit/delete scheduled jobs"});
        BASE_PERMISSIONS.put("feature-flags:manage", new String[]{"Feature Flags", "Toggle or edit feature flags"});
        BASE_PERMISSIONS.put("cache:manage", new String[]{"Cache", "Inspect or evict cache entries"});
    }

    @Override
    @Transactional
    public void run(String... args) {
        Set<Permission> allPermissions = new HashSet<>();
        BASE_PERMISSIONS.forEach((code, meta) -> allPermissions.add(seedPermissionIfMissing(code, meta[0], meta[1])));

        seedRoleIfMissing(Role.ROLE_ADMIN, "Administrator", "Full platform access", allPermissions);
        seedRoleIfMissing(Role.ROLE_USER, "User", "Standard, unprivileged account", Set.of());
    }

    private Permission seedPermissionIfMissing(String code, String category, String description) {
        return permissionRepository.findByCode(code).orElseGet(() -> {
            Permission saved = permissionRepository.save(Permission.builder()
                    .code(code)
                    .category(category)
                    .description(description)
                    .build());
            log.info("Seeded permission '{}'", code);
            return saved;
        });
    }

    private void seedRoleIfMissing(Role roleKey, String name, String description, Set<Permission> permissions) {
        if (roleDefinitionRepository.existsByRoleKey(roleKey)) {
            return;
        }
        roleDefinitionRepository.save(RoleDefinition.builder()
                .roleKey(roleKey)
                .name(name)
                .description(description)
                .permissions(new HashSet<>(permissions))
                .build());
        log.info("Seeded role definition for {}", roleKey);
    }
}
