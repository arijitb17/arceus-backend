package com.arijit.monitoringservice.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /monitoring/projects/{projectId}/usage - the Developer Portal's Usage tab. */
public record ProjectUsageResponse(
        LocalDate from,
        LocalDate to,
        long totalRequests,
        long totalErrors,
        List<UsageDayPoint> points
) {}
