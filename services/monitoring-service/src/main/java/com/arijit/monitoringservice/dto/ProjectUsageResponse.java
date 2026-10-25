package com.arijit.monitoringservice.dto;

import java.time.LocalDate;
import java.util.List;

public record ProjectUsageResponse(
        LocalDate from,
        LocalDate to,
        long totalRequests,
        long totalErrors,
        List<UsageDayPoint> points
) {}
