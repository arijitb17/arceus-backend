package com.arijit.monitoringservice.dto;

import java.util.List;

public record ServiceMetricsDetailResponse(
        ServiceMetricsDto current,
        List<ServiceMetricsDto> history
) {
}
