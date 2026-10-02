package com.arijit.organizationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (organization, user) membership. The owner is also a member
 * row (role=OWNER) created at organization-creation time - there's no
 * separate "owner" column on Organization to keep in sync, the same way
 * Deployment doesn't duplicate approver identity outside its own audit
 * trail.
 * <p>
 * {@code userId}/{@code invitedBy} hold the user's <b>email</b>, not a
 * UUID - see RoleHeaderAuthFilter's javadoc for why (the Gateway only
 * propagates email downstream, matching deployment-service's own
 * {@code createdBy} convention). Kept named {@code userId} rather than
 * renamed to avoid a larger diff across this first pass; a follow-up
 * rename to {@code userEmail} is low-risk and listed in the roadmap.
 */
@Entity
@Table(name = "organization_members", indexes = {
        @Index(name = "idx_org_member_org", columnList = "organizationId"),
        @Index(name = "idx_org_member_user", columnList = "userId")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_org_member_org_user", columnNames = {"organizationId", "userId"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Column(nullable = false, length = 200)
    private String invitedBy;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;
}
