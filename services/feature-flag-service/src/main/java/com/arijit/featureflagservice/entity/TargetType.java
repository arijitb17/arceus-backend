package com.arijit.featureflagservice.entity;

/**
 * Only GLOBAL and ENVIRONMENT are accepted by FlagValidationService today - USER, ROLE, and TENANT
 * are reserved so the public API/DB shape never has to change when per-user, per-role, or per-
 * tenant targeting (and eventually A/B testing, rule engines) are layered on.
 */
public enum TargetType {
    GLOBAL,
    ENVIRONMENT,
    USER,
    ROLE,
    TENANT
}
