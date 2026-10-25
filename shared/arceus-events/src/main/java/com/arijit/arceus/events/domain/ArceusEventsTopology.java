package com.arijit.arceus.events.domain;


public final class ArceusEventsTopology {
    public static final String EVENTS_EXCHANGE = "arceus.events.exchange";

    public static final String NOTIFICATION_QUEUE = "arceus.notification.events.queue";

    public static final String AUDIT_QUEUE = "arceus.audit.events.queue";

    public static final String DEAD_LETTER_EXCHANGE = "arceus.events.dlx";

    public static final String DLQ_SUFFIX = ".dlq";

    public static final String ROUTING_KEY_ALL = "#";

    public static final String RK_USER_REGISTERED = "user.registered";
    public static final String RK_LOGIN_SUCCESSFUL = "login.successful";
    public static final String RK_LOGIN_FAILED = "login.failed";
    public static final String RK_PASSWORD_CHANGED = "password.changed";
    public static final String RK_EMAIL_CHANGED = "email.changed";
    public static final String RK_PHONE_CHANGED = "phone.changed";
    public static final String RK_USER_LOGGED_OUT = "user.loggedout";
    public static final String RK_API_KEY_CREATED = "apikey.created";
    public static final String RK_API_KEY_REVOKED = "apikey.revoked";

    public static final String RK_JOB_SCHEDULED = "job.scheduled";
    public static final String RK_JOB_STARTED = "job.started";
    public static final String RK_JOB_COMPLETED = "job.completed";
    public static final String RK_JOB_FAILED = "job.failed";
    public static final String RK_JOB_CANCELLED = "job.cancelled";
    public static final String RK_JOB_EXPIRED = "job.expired";

    public static final String RK_FLAG_CREATED = "flag.created";
    public static final String RK_FLAG_UPDATED = "flag.updated";
    public static final String RK_FLAG_ENABLED = "flag.enabled";
    public static final String RK_FLAG_DISABLED = "flag.disabled";
    public static final String RK_FLAG_DELETED = "flag.deleted";


    public static final String RK_SERVICE_DOWN = "service.down";
    public static final String RK_SERVICE_RECOVERED = "service.recovered";
    public static final String RK_ALERT_TRIGGERED = "alert.triggered";
    public static final String RK_ALERT_RESOLVED = "alert.resolved";

    public static final String RK_DEPLOYMENT_CREATED = "deployment.created";
    public static final String RK_DEPLOYMENT_STARTED = "deployment.started";
    public static final String RK_DEPLOYMENT_COMPLETED = "deployment.completed";
    public static final String RK_DEPLOYMENT_FAILED = "deployment.failed";
    public static final String RK_DEPLOYMENT_ROLLED_BACK = "deployment.rolledback";
    public static final String RK_DEPLOYMENT_CANCELLED = "deployment.cancelled";

    public static final String RK_ORGANIZATION_CREATED = "organization.created";
    public static final String RK_ORGANIZATION_MEMBER_ADDED = "organization.member_added";
    public static final String RK_ORGANIZATION_MEMBER_REMOVED = "organization.member_removed";
    public static final String RK_PROJECT_CREATED = "project.created";
    public static final String RK_PROJECT_DELETED = "project.deleted";
    public static final String RK_MODULE_ENABLE_REQUESTED = "module.enable_requested";
    public static final String RK_MODULE_DISABLE_REQUESTED = "module.disable_requested";
    public static final String RK_SECRET_CREATED = "secret.created";
    public static final String RK_SECRET_REVOKED = "secret.revoked";

    public static final String RK_USAGE_REQUEST_RECORDED = "usage.request_recorded";

    private ArceusEventsTopology() {}
}
