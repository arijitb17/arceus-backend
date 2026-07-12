package com.arijit.arceus.events.deployment;

/**
 * Topology for deployment-service's own internal messaging - deliberately
 * separate from {@link com.arijit.arceus.events.domain.ArceusEventsTopology},
 * which is the platform-wide "something happened" bus deployment-service
 * publishes its {@code deployment.*} lifecycle events onto (and the same
 * bus monitoring-service publishes {@code service.down}/{@code
 * service.recovered}/{@code alert.*} onto). Everything here is either
 * deployment-service being nudged by another service/tool, or
 * deployment-service subscribing to a slice of the shared bus for its own
 * purposes - not a fact being broadcast to every subscriber.
 * <p>
 * Two queues, two purposes:
 * <ul>
 *   <li>{@link #APPROVAL_DECISIONS_QUEUE} - "approve/reject deployment X"
 *   requests. {@code POST /deployments/{id}/approve} is the primary,
 *   synchronous path (a human clicking a button through the Gateway); this
 *   queue is the asynchronous ingress point for the same decision - e.g. a
 *   future ChatOps/Slack approval bot that publishes here instead of
 *   calling the REST API directly. Deliberately separate from the
 *   shared-bus deployment.* events, since an approval decision is an
 *   instruction to deployment-service, not a fact about something that
 *   already happened.</li>
 *   <li>{@link #HEALTH_EVENTS_QUEUE} - deployment-service's own queue,
 *   bound to the shared {@code arceus.events.exchange} (not a queue
 *   monitoring-service or anyone else needs to know about) filtered to the
 *   {@code service.down}/{@code service.recovered}/{@code alert.triggered}/
 *   {@code alert.resolved} routing keys - this is the "Consume Events:
 *   Monitoring health events" surface, letting a currently-DEPLOYING/
 *   RUNNING deployment's health picture stay current without
 *   deployment-service polling Actuator endpoints itself.</li>
 * </ul>
 */
public final class DeploymentMessagingTopology {

    /** Where async approval/rejection decisions are published to and consumed from. */
    public static final String APPROVAL_DECISIONS_QUEUE = "arceus.deployment.approval-decisions.queue";

    /**
     * deployment-service's own subscription onto the platform-wide domain
     * event bus, filtered (via binding keys, not a wildcard) to
     * monitoring-service's health-transition events only.
     */
    public static final String HEALTH_EVENTS_QUEUE = "arceus.deployment.health-events.queue";

    /** Direct exchange {@link #APPROVAL_DECISIONS_QUEUE} is published through. */
    public static final String INTERNAL_EXCHANGE = "arceus.deployment.internal.exchange";

    /**
     * deployment-service's own dead-letter exchange - deliberately separate
     * from {@code com.arijit.arceus.events.domain.ArceusEventsTopology.DEAD_LETTER_EXCHANGE},
     * which is for the platform-wide domain-event bus. A poison approval
     * message or a bug in the health-event consumer should only ever be
     * able to affect deployment-service's own queues.
     */
    public static final String DEAD_LETTER_EXCHANGE = "arceus.deployment.dlx";

    /** Appended to a queue's own name to get its dead-letter queue's name. */
    public static final String DLQ_SUFFIX = ".dlq";

    private DeploymentMessagingTopology() {}
}
