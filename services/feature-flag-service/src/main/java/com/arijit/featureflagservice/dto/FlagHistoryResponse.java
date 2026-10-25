package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.FlagAuditLog;
import com.arijit.featureflagservice.entity.FlagVersion;

import java.time.Instant;
import java.util.UUID;

public record FlagHistoryResponse(
        UUID id,
        String flagKey,
        Long versionNumber,
        String action,
        String actor,
        String oldValue,
        String newValue,
        String snapshot,
        Instant timestamp
) {
    public static FlagHistoryResponse fromAudit(FlagAuditLog log) {
        return new FlagHistoryResponse(
                log.getId(), log.getFlagKey(), null, log.getAction(), log.getActor(),
                log.getOldValue(), log.getNewValue(), null, log.getTimestamp());
    }

    public static FlagHistoryResponse fromVersion(FlagVersion version) {
        return new FlagHistoryResponse(
                version.getId(), version.getFlagKey(), version.getVersionNumber(),
                version.getChangeType().name(), version.getChangedBy(), null, null,
                version.getSnapshot(), version.getChangedAt());
    }
}
