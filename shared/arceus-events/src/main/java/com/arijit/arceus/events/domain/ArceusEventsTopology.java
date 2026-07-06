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

    private ArceusEventsTopology() {}
}
