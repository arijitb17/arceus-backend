package com.arijit.monitoringservice.dto;

import java.util.List;

/** GET /services/{service} - current status plus recent Service Status History. */
public record ServiceDetailResponse(
        ServiceHealthDto current,
        List<ServiceHealthDto> history
) {
}
