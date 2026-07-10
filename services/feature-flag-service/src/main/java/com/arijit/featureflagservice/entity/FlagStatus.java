package com.arijit.featureflagservice.entity;

/**
 * A flag's on/off switch, independent of its value. A DISABLED flag
 * always evaluates as "off" (the caller's default, if one was supplied)
 * regardless of rolloutPercentage or the stored value - enabling a flag
 * is what makes rollout percentage and the stored value actually matter.
 */
public enum FlagStatus {
    ENABLED,
    DISABLED
}
