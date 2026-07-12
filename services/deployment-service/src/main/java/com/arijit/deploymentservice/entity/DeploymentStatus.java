package com.arijit.deploymentservice.entity;

/**
 * A deployment's lifecycle, exactly the eight statuses the platform spec
 * lists - no additions, unlike scheduler-service's JobStatus/PAUSED. RUNNING
 * plays the role a separate "COMPLETED" would for a job: it means this
 * deployment is the one currently live for its service+environment, right
 * up until it's superseded by a newer deployment or explicitly rolled back.
 */
public enum DeploymentStatus {
    /** Just created, no approval required for its environment - eligible for immediate POST /deploy. */
    CREATED,
    /** Just created, its environment requires approval before it can be deployed. */
    PENDING,
    /** Approved by an authorized caller - eligible for POST /deploy. */
    APPROVED,
    /** Deploy in progress: validation, execution, and post-deploy health verification are running. */
    DEPLOYING,
    /** Deployed and verified healthy - this is the current live version for its service+environment. */
    RUNNING,
    /** Deploy failed validation, execution, or post-deploy health verification. Terminal. */
    FAILED,
    /** Superseded by a rollback that restored an earlier version. Terminal. */
    ROLLED_BACK,
    /** Cancelled before it ever deployed. Terminal. */
    CANCELLED;

    /** Statuses a deployment can never leave on its own - only an explicit caller action changes them further, and even then, never. */
    public boolean isTerminal() {
        return this == FAILED || this == ROLLED_BACK || this == CANCELLED;
    }
}
