package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// One row per value of the {@link Role} enum (seeded by RbacSeeder, see
// config/). This is what turns the two hardcoded roles into something a
// "Permissions" admin screen can actually manage: an admin can't invent a
// brand-new Spring Security role (that still requires a code change to
// mean anything at the filter-chain level), but they CAN curate exactly
// which permissions ROLE_ADMIN vs ROLE_USER carries, and any service can
// look that assignment up instead of hardcoding "admin can do everything".
@Entity
@Table(name = "role_definitions", uniqueConstraints = @UniqueConstraint(columnNames = "role_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDefinition {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_key", nullable = false, unique = true)
    private Role roleKey;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_definition_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
