package com.arijit.organizationservice.entity;

/**
 * The three environments every project gets by default (per the platform
 * spec). Deliberately a smaller set than deployment-service's own
 * Environment enum (DEVELOPMENT/TESTING/STAGING/PRODUCTION, which tracks
 * Arceus's *own* internal releases) - tenant projects don't get a TESTING
 * environment by default. Kept as a distinct type rather than reusing
 * deployment-service's enum so the two services' concepts of "environment"
 * (Arceus's own release tracking vs. a tenant's isolated resource scope)
 * can evolve independently.
 */
public enum EnvironmentType {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}
