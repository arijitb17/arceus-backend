package com.arijit.arceus.events.deployment;


public final class DeploymentMessagingTopology {
    public static final String APPROVAL_DECISIONS_QUEUE = "arceus.deployment.approval-decisions.queue";

    public static final String HEALTH_EVENTS_QUEUE = "arceus.deployment.health-events.queue";

    public static final String MODULE_PROVISIONING_QUEUE = "arceus.deployment.module-provisioning.queue";

    public static final String INTERNAL_EXCHANGE = "arceus.deployment.internal.exchange";

    public static final String DEAD_LETTER_EXCHANGE = "arceus.deployment.dlx";

    public static final String DLQ_SUFFIX = ".dlq";

    private DeploymentMessagingTopology() {}
}
