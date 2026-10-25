package com.arijit.featureflagservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flag_versions", indexes = {
        @Index(name = "idx_flag_version_flag_id", columnList = "flagId,versionNumber")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID flagId;

    @Column(nullable = false, length = 200)
    private String flagKey;

    @Column(nullable = false)
    private long versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeType changeType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(nullable = false, length = 200)
    private String changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    public enum ChangeType {
        CREATE, UPDATE, ENABLE, DISABLE, DELETE
    }
}
