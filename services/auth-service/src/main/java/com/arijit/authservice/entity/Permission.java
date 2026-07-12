package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// A single named capability, e.g. "users:write" or "notifications:send".
// Deliberately just a flat catalog with no code-level enforcement wired up
// yet (that would mean auditing every controller in every service) - this
// is the data model the "Permissions" admin screen needs to exist, and the
// building block RoleDefinition attaches to actual roles. Kept separate
// from the {@link Role} enum on purpose: Role stays the coarse thing Spring
// Security checks on every request (hasRole('ADMIN')), while Permission is
// the fine-grained thing an admin curates and assigns to a role for
// UI/business-logic purposes.
@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue
    private UUID id;

    // A stable machine key, e.g. "users:read", "users:write",
    // "notifications:send". Convention: "<resource>:<action>", not enforced.
    @Column(nullable = false, unique = true)
    private String code;

    // Grouping for the UI, e.g. "Users", "Notifications", "Deployments".
    // Free text on purpose - new resources shouldn't require a code change.
    @Column(nullable = false)
    private String category;

    private String description;

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
