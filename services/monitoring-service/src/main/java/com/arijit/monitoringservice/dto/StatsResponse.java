package com.arijit.monitoringservice.dto;

import java.time.Instant;

public record StatsResponse(
        int totalServices,
        int servicesUp,
        int servicesDown,
        int servicesDegraded,
        long activeAlertsCount,
        long alertsTriggeredLast24h,
        Double avgPlatformResponseTimeMs,
        Double platformAvailabilityPercentage,
        Instant generatedAt
) {
}
