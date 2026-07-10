package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagVersion;
import com.arijit.featureflagservice.repository.FlagVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Records one immutable {@link FlagVersion} snapshot per state-changing
 * operation - called from FlagService right after each successful save,
 * inside the same transaction, so a version row and the flag state it
 * describes are always consistent. A snapshot failure (e.g. a
 * serialization bug) is logged and swallowed rather than rolling back the
 * flag change itself - version history is valuable but not as important
 * as the mutation it's recording actually succeeding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlagVersionService {

    private final FlagVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public void record(FeatureFlag flag, FlagVersion.ChangeType changeType, String actor) {
        try {
            String snapshot = objectMapper.writeValueAsString(flag);
            FlagVersion version = FlagVersion.builder()
                    .flagId(flag.getId())
                    .flagKey(flag.getFlagKey())
                    .versionNumber(flag.getVersion() != null ? flag.getVersion() : 0L)
                    .changeType(changeType)
                    .snapshot(snapshot)
                    .changedBy(actor)
                    .changedAt(Instant.now())
                    .build();
            versionRepository.save(version);
        } catch (Exception e) {
            log.warn("Failed to record version snapshot for flag {}: {}", flag.getFlagKey(), e.getMessage());
        }
    }
}
