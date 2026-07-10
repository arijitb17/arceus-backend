package com.arijit.schedulerservice.entity;

/**
 * How a job's nextFireAt is (re)computed - see TriggerCalculator for the
 * actual math. IMMEDIATE and DELAY are one-time; CRON, FIXED_RATE, and
 * FIXED_DELAY are recurring (a completed run always computes another
 * nextFireAt instead of going terminal).
 */
public enum JobTriggerType {
    /** Fires once, as soon as the poller's next tick sees it - nextFireAt is set to "now" at creation time. */
    IMMEDIATE,
    /** Fires once, delayMs after creation. */
    DELAY,
    /** Fires repeatedly per a standard cron expression (seconds-optional, per Spring's CronExpression). */
    CRON,
    /** Fires repeatedly every fixedRateMs, measured from the previous scheduled fire time (not from completion). */
    FIXED_RATE,
    /** Fires repeatedly, fixedDelayMs after the previous run's completion (not a fixed clock schedule). */
    FIXED_DELAY;

    public boolean isRecurring() {
        return this == CRON || this == FIXED_RATE || this == FIXED_DELAY;
    }
}
