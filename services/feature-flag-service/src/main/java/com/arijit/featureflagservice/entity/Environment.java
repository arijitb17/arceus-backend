package com.arijit.featureflagservice.entity;

/**
 * Which deployment environment a flag row applies to. GLOBAL is the
 * escape hatch for a flag with one value across every environment - see
 * TargetType.GLOBAL, which is what actually makes a flag environment-
 * agnostic; a flag with targetType=ENVIRONMENT always has one row per
 * environment it's defined in, each independently created/updated/
 * enabled/disabled.
 */
public enum Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    GLOBAL
}
