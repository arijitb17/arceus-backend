package com.arijit.deploymentservice.dto;

public record HealthResponse(
        String status,
        long activeDeployments,
        long pendingApprovals,
        String databaseStatus,
        String rabbitStatus,
        String monitoringConnectivity,
        String schedulerConnectivity
) {
}
