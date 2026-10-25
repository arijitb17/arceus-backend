package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.dto.AlertEventResponse;
import com.arijit.monitoringservice.entity.AlertEvent;

final class AlertEventMapper {
    private AlertEventMapper() {
    }

    static AlertEventResponse toDto(AlertEvent event) {
        return new AlertEventResponse(
                event.getId(),
                event.getRuleName(),
                event.getType().name(),
                event.getServiceName(),
                event.getSeverity().name(),
                event.getMessage(),
                event.getState().name(),
                event.getMetricValue(),
                event.getTriggeredAt(),
                event.getResolvedAt()
        );
    }
}
