package com.arijit.featureflagservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable snapshot per state-changing operation on a
 * {@link FeatureFlag} - full JSON snapshot of the flag's fields at that
 * point in time, so GET /flags/history can show exactly what a flag
 * looked like at any past version without reconstructing it from a diff
 * chain. {@link #versionNumber} mirrors the flag's own {@code version}
 * field at the moment of the change (1-based, monotonically increasing
 * per flag row).
 * <p>
 * Deliberately separate from {@link FlagAuditLog}: this answers "what did
 * the flag look like", the audit log answers "who did what and when" -
 * together they cover both "Version History" and "Audit History" from the
 * spec without conflating the two concerns into one table.
 */
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

    /** Full JSON snapshot of the flag's state after this change - see FlagVersionService. */
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
