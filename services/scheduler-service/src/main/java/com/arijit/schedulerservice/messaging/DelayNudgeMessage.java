package com.arijit.schedulerservice.messaging;

/**
 * Body carried through the delay-queue dead-letter pattern (see
 * SchedulerMessagingTopology). Published to DELAY_QUEUE with a per-message
 * TTL equal to the job's delayMs; once that TTL expires, RabbitMQ
 * dead-letters this same body into DELAY_READY_QUEUE where
 * DelayReadyConsumer picks it up.
 */
public record DelayNudgeMessage(String jobId) {
}
