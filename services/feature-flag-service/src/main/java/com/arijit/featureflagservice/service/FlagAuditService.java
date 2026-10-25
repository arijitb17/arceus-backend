package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FlagAuditLog;
import com.arijit.featureflagservice.repository.FlagAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagAuditService {
    private static final String MDC_CORRELATION_ID_KEY = "correlationId";

    private final FlagAuditLogRepository auditLogRepository;

    public void record(UUID flagId, String flagKey, Environment environment, String action, String actor, String oldValue, String newValue) {
        try {
            FlagAuditLog entry = FlagAuditLog.builder()
                    .flagId(flagId)
                    .flagKey(flagKey)
                    .environment(environment)
                    .action(action)
                    .actor(actor != null ? actor : "unknown")
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .correlationId(MDC.get(MDC_CORRELATION_ID_KEY))
                    .timestamp(Instant.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to record audit entry for flag {}: {}", flagKey, e.getMessage());
        }
    }
}
