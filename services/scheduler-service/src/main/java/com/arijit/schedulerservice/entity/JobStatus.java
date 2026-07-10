package com.arijit.schedulerservice.entity;

/**
 * A job's lifecycle. PAUSED is the one addition beyond the platform's
 * literal PENDING/SCHEDULED/RUNNING/COMPLETED/FAILED/RETRYING/CANCELLED/
 * EXPIRED list - Pause/Resume needs a state that isn't CANCELLED (resumable,
 * not final) and isn't SCHEDULED (must NOT be picked up by the poller),
 * so it gets its own first-class status rather than overloading one of
 * the others.
 */
public enum JobStatus {
    /** Persisted, not yet given a nextFireAt (e.g. still being validated/created). Transient - moves to SCHEDULED immediately on creation in practice. */
    PENDING,
    /** Has a nextFireAt in the future (or due now, waiting for the poller's next tick); eligible for pickup. */
    SCHEDULED,
    /** Currently executing on a worker thread. */
    RUNNING,
    /** Finished successfully. Terminal for one-time jobs; recurring jobs return to SCHEDULED for their next fire instead. */
    COMPLETED,
    /** Exhausted all retries. Terminal - see JobDeadLetter. */
    FAILED,
    /** Failed, but under max-retries - waiting on nextFireAt (the backoff delay) before another attempt. */
    RETRYING,
    /** Cancelled by a caller; never runs again. Terminal. */
    CANCELLED,
    /** Never ran (or a recurring job never fired again) before its expiresAt passed. Terminal. */
    EXPIRED,
    /** Paused by a caller; excluded from pickup until resumed. Not terminal. */
    PAUSED;

    /** Statuses a job can never leave on its own - only an explicit caller action, or nothing, changes them further. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
