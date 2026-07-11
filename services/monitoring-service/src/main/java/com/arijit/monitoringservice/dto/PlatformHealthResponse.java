package com.arijit.monitoringservice.dto;

import java.time.Instant;
import java.util.List;

public record PlatformHealthResponse(
        String overallStatus,
        List<ServiceHealthDto> services,
        Instant generatedAt
) {
}
