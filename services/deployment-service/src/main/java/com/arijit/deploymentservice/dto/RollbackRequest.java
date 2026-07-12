package com.arijit.deploymentservice.dto;

/**
 * POST /deployments/{id}/rollback body. The path id identifies the
 * currently-live deployment to roll back *from*; {@code targetVersion} is
 * optional - empty rolls back to the version immediately prior to it (see
 * VersionService#previousVersion), otherwise rolls back to that specific
 * earlier version if it was ever live for this service+environment.
 * {@code initiatedBy} is also optional - DeploymentController falls back
 * to the authenticated caller's identity when it's blank, same as
 * AlertController does for {@code createdBy} on POST /alerts.
 */
public record RollbackRequest(
        String targetVersion,
        String initiatedBy,
        String correlationId
) {
}
