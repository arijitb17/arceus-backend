package com.arijit.featureflagservice.entity;

/**
 * What a flag's targeting is scoped to. Only GLOBAL and ENVIRONMENT are
 * accepted by FlagValidationService today - USER, ROLE, and TENANT are
 * reserved so the public API/DB shape never has to change when per-user,
 * per-role, or per-tenant targeting (and eventually A/B testing, rule
 * engines) are layered on. A flag row always carries whichever TargetType
 * it was created with; evaluation logic branches on it, so adding a new
 * case there is additive rather than a breaking change to existing flags.
 */
public enum TargetType {
    /** One value across every environment - environment on the row is ignored at evaluation time. */
    GLOBAL,
    /** One value per environment - the common case; a key may have up to one row per Environment. */
    ENVIRONMENT,
    /** Reserved - not yet implemented. */
    USER,
    /** Reserved - not yet implemented. */
    ROLE,
    /** Reserved - not yet implemented. */
    TENANT
}
