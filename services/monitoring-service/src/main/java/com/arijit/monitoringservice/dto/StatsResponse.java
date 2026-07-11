package com.arijit.monitoringservice.dto;

import java.time.Instant;

/** GET /stats - Platform Statistics, a lighter-weight numeric summary than /dashboard. */
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
