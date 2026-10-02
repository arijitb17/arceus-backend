package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.ModuleEnablement;

import java.time.Instant;
import java.util.UUID;

public record ModuleEnablementResponse(
        UUID id,
        String module,
        String environment,
        String status,
        String lastError,
        Instant updatedAt
) {
    public static ModuleEnablementResponse from(ModuleEnablement m) {
        return new ModuleEnablementResponse(m.getId(), m.getModule().name(), m.getEnvironment().name(),
                m.getStatus().name(), m.getLastError(), m.getUpdatedAt());
    }
}
