package com.arijit.monitoringservice.dto;

import java.util.List;

public record ServiceDetailResponse(
        ServiceHealthDto current,
        List<ServiceHealthDto> history
) {
}
