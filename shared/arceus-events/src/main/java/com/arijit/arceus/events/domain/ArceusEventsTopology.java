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

    // scheduler-service's job lifecycle events. Published onto this same
    // exchange (rather than a dedicated one) so audit-service's existing
    // "#" binding picks them up for free, and so any other service can
    // subscribe to a job's lifecycle the exact same way it already
    // subscribes to auth-service's events - one bus, one mental model,
    // regardless of which service happens to be the publisher.
    public static final String RK_JOB_SCHEDULED = "job.scheduled";
    public static final String RK_JOB_STARTED = "job.started";
    public static final String RK_JOB_COMPLETED = "job.completed";
    public static final String RK_JOB_FAILED = "job.failed";
    public static final String RK_JOB_CANCELLED = "job.cancelled";
    public static final String RK_JOB_EXPIRED = "job.expired";

    // feature-flag-service's config-change events. Same reasoning as the
    // job.* keys above: published onto this one shared exchange so
    // audit-service's existing "#" binding captures every flag mutation
    // for free, and so any future subscriber (e.g. a dashboard, or a
    // service that wants to react to a flag flipping) can bind its own
    // queue without feature-flag-service ever needing to know it exists.
    public static final String RK_FLAG_CREATED = "flag.created";
    public static final String RK_FLAG_UPDATED = "flag.updated";
    public static final String RK_FLAG_ENABLED = "flag.enabled";
    public static final String RK_FLAG_DISABLED = "flag.disabled";
    public static final String RK_FLAG_DELETED = "flag.deleted";

    // monitoring-service's platform-observability events. Same reasoning as
    // job.* and flag.* above: published onto this one shared exchange so
    // audit-service's existing "#" binding captures every service-down /
    // alert transition for free, and so any future subscriber (e.g. a
    // status-page service, once one exists) can bind its own queue without
    // monitoring-service ever needing to know it exists. Deliberately not
    // "notification integrations" (out of scope per the platform's current
    // stage) - just the same fire-and-forget domain event every other
    // publisher already emits.
    public static final String RK_SERVICE_DOWN = "service.down";
    public static final String RK_SERVICE_RECOVERED = "service.recovered";
    public static final String RK_ALERT_TRIGGERED = "alert.triggered";
    public static final String RK_ALERT_RESOLVED = "alert.resolved";

    // deployment-service's release lifecycle events. Same reasoning as
    // job.*/flag.*/service.* above: published onto this one shared exchange
    // so audit-service's existing "#" binding captures every deployment
    // action for free ("Audit every deployment action" per the platform
    // security spec), and so any future subscriber (a release dashboard, a
    // ChatOps bot posting "prod deploy succeeded") can bind its own queue
    // without deployment-service ever needing to know it exists.
    public static final String RK_DEPLOYMENT_CREATED = "deployment.created";
    public static final String RK_DEPLOYMENT_STARTED = "deployment.started";
    public static final String RK_DEPLOYMENT_COMPLETED = "deployment.completed";
    public static final String RK_DEPLOYMENT_FAILED = "deployment.failed";
    public static final String RK_DEPLOYMENT_ROLLED_BACK = "deployment.rolledback";
    public static final String RK_DEPLOYMENT_CANCELLED = "deployment.cancelled";

    // organization-service's tenant-hierarchy events. Same reasoning as
    // every other block above: published onto this one shared exchange so
    // audit-service's existing "#" binding captures every org/project/
    // module/secret mutation for free. deployment-service additionally
    // binds its own queue to the module.enable_requested/disable_requested
    // keys specifically - since those are the events the requesting client
    // waits on the result of, per ModuleEnablementService's javadoc.
    public static final String RK_ORGANIZATION_CREATED = "organization.created";
    public static final String RK_ORGANIZATION_MEMBER_ADDED = "organization.member_added";
    public static final String RK_ORGANIZATION_MEMBER_REMOVED = "organization.member_removed";
    public static final String RK_PROJECT_CREATED = "project.created";
    public static final String RK_PROJECT_DELETED = "project.deleted";
    public static final String RK_MODULE_ENABLE_REQUESTED = "module.enable_requested";
    public static final String RK_MODULE_DISABLE_REQUESTED = "module.disable_requested";
    public static final String RK_SECRET_CREATED = "secret.created";
    public static final String RK_SECRET_REVOKED = "secret.revoked";

    // Usage tracking (architecture design's usage/logs work, monitoring
    // extension). Published by gateway-service - the one place every
    // request already passes through - only for requests ApiKeyAuthFilter
    // authenticated (i.e. SDK/API traffic with a resolved project scope,
    // not Portal browser navigation). monitoring-service is the sole
    // consumer, aggregating into daily rollups rather than storing one row
    // per request - see UsageRollup's javadoc.
    public static final String RK_USAGE_REQUEST_RECORDED = "usage.request_recorded";

    private ArceusEventsTopology() {}
}
