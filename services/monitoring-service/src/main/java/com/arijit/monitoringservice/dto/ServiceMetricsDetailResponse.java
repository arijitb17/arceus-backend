package com.arijit.monitoringservice.dto;

import java.util.List;

/** GET /metrics/{service} - current metrics plus recent Metrics History. */
public record ServiceMetricsDetailResponse(
        ServiceMetricsDto current,
        List<ServiceMetricsDto> history
) {
}
