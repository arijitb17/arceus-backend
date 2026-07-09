package com.arijit.arceus.events.domain;

/**
 * Topology for the platform-wide domain event bus (separate from the
 * dedicated OTP queue in {@code com.arijit.arceus.events.otp}).
 *
 * Auth-service (today) and any future publisher sends every domain event to
 * one topic exchange. Each consumer service declares and binds its own
 * queue with whatever routing-key pattern it actually cares about, so
 * adding a new subscriber never requires touching the publisher.
 */
public final class ArceusEventsTopology {

    /** Single topic exchange every domain event is published to. */
    public static final String EVENTS_EXCHANGE = "arceus.events.exchange";

    /** notification-service's own queue - only wants events it can act on. */
    public static final String NOTIFICATION_QUEUE = "arceus.notification.events.queue";

    /** audit-service's own queue - wants everything, for the audit trail. */
    public static final String AUDIT_QUEUE = "arceus.audit.events.queue";

    /**
     * Where a message ends up after exhausting
     * spring.rabbitmq.listener.simple.retry's attempts - a poison message
     * or a prolonged DB outage parks it here instead of looping forever or
     * being silently dropped. One shared dead-letter exchange for every
     * domain-event queue; each queue's dead-lettered messages land in their
     * own suffixed queue (see NOTIFICATION_QUEUE + DLQ_SUFFIX, etc), bound
     * with the original queue name as the routing key (RabbitMQ's default
     * dead-letter behavior preserves it).
     */
    public static final String DEAD_LETTER_EXCHANGE = "arceus.events.dlx";

    /** Appended to a queue's own name to get its dead-letter queue's name, e.g. "arceus.audit.events.queue.dlq". */
    public static final String DLQ_SUFFIX = ".dlq";

    /** Binding pattern that matches every routing key below. */
    public static final String ROUTING_KEY_ALL = "#";

    // ---------- Routing keys, one per event type ----------
    public static final String RK_USER_REGISTERED = "user.registered";
    public static final String RK_LOGIN_SUCCESSFUL = "login.successful";
    public static final String RK_LOGIN_FAILED = "login.failed";
    public static final String RK_PASSWORD_CHANGED = "password.changed";
    public static final String RK_EMAIL_CHANGED = "email.changed";
    public static final String RK_PHONE_CHANGED = "phone.changed";
    public static final String RK_USER_LOGGED_OUT = "user.loggedout";
    public static final String RK_API_KEY_CREATED = "apikey.created";
    public static final String RK_API_KEY_REVOKED = "apikey.revoked";

    private ArceusEventsTopology() {}
}
