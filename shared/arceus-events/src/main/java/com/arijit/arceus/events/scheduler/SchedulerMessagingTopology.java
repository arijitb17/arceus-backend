package com.arijit.arceus.events.scheduler;

public final class SchedulerMessagingTopology {
    public static final String RETRY_REQUEST_QUEUE = "arceus.scheduler.retry-requests.queue";

    public static final String DELAY_QUEUE = "arceus.scheduler.delay.queue";

    public static final String DELAY_READY_QUEUE = "arceus.scheduler.delay.ready.queue";

    public static final String INTERNAL_EXCHANGE = "arceus.scheduler.internal.exchange";

    public static final String DEAD_LETTER_EXCHANGE = "arceus.scheduler.dlx";

    public static final String DLQ_SUFFIX = ".dlq";

    private SchedulerMessagingTopology() {}
}
