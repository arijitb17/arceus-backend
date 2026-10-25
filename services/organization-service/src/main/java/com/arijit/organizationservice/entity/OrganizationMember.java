package com.arijit.organizationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

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
