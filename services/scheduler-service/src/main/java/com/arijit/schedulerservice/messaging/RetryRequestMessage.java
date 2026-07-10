package com.arijit.schedulerservice.messaging;

/**
 * Body published to {@code SchedulerMessagingTopology.RETRY_REQUEST_QUEUE}.
 * Any platform service can publish one of these directly (same
 * exchange/routing-key any other service binds its own queues with) to
 * ask scheduler-service to retry a job now, without going through the
 * REST or gRPC API at all - {@code POST /jobs/{id}/retry} and the
 * {@code RetryJob} RPC are just convenience wrappers that publish the
 * same message.
 */
public record RetryRequestMessage(String jobId) {
}
