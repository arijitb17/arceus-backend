package com.arijit.arceus.events.scheduler;

/**
 * Topology for scheduler-service's own internal messaging - deliberately
 * separate from {@link com.arijit.arceus.events.domain.ArceusEventsTopology},
 * which is the platform-wide "something happened" bus scheduler-service
 * publishes its {@code job.*} lifecycle events onto. Everything here is
 * scheduler-service talking to itself (or being talked to by another
 * service that wants to nudge it) rather than a fact being broadcast to
 * every subscriber.
 * <p>
 * Two queues, two purposes:
 * <ul>
 *   <li>{@link #RETRY_REQUEST_QUEUE} - "please retry this job now" requests.
 *   scheduler-service's own {@code POST /jobs/{id}/retry} and
 *   {@code RetryJob} gRPC/REST callers publish here instead of mutating the
 *   job row synchronously, so a retry request is durable (survives a
 *   scheduler-service restart between request and effect) and so any other
 *   platform service can request a retry the exact same way, without
 *   needing scheduler-service's REST/gRPC surface at all.</li>
 *   <li>{@link #DELAY_QUEUE} / {@link #DELAY_READY_QUEUE} - a RabbitMQ
 *   dead-letter delay pattern (per-message TTL + no consumer on the delay
 *   queue itself) used as a low-latency nudge for DELAY-triggered jobs, so
 *   a short delay doesn't have to wait for the next database poll. The
 *   database poller remains the source of truth and safety net either
 *   way - this is an optimization, not a dependency.</li>
 * </ul>
 */
public final class SchedulerMessagingTopology {

    /** Where retry requests are published to and consumed from. */
    public static final String RETRY_REQUEST_QUEUE = "arceus.scheduler.retry-requests.queue";

    /**
     * A job's delay is encoded as this message's TTL (see RabbitMQConfig) -
     * nothing ever consumes this queue directly, it only exists so RabbitMQ
     * dead-letters expired messages into {@link #DELAY_READY_QUEUE}.
     */
    public static final String DELAY_QUEUE = "arceus.scheduler.delay.queue";

    /** Where a delayed job's "ready" message lands once its TTL expires. */
    public static final String DELAY_READY_QUEUE = "arceus.scheduler.delay.ready.queue";

    /** Direct exchange both internal queues above are published through. */
    public static final String INTERNAL_EXCHANGE = "arceus.scheduler.internal.exchange";

    /**
     * scheduler-service's own dead-letter exchange - deliberately separate
     * from {@code com.arijit.arceus.events.domain.ArceusEventsTopology.DEAD_LETTER_EXCHANGE},
     * which is for the platform-wide domain-event bus. A poison retry
     * request or a bug in the delay-ready path should only ever be able to
     * affect scheduler-service's own queues.
     */
    public static final String DEAD_LETTER_EXCHANGE = "arceus.scheduler.dlx";

    /** Appended to a queue's own name to get its dead-letter queue's name. */
    public static final String DLQ_SUFFIX = ".dlq";

    private SchedulerMessagingTopology() {}
}
