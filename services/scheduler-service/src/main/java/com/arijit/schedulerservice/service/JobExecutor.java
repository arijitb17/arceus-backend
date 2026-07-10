package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.Job;

/**
 * One implementation per {@link ExecutionTarget}. Adding a new delivery
 * mechanism later (gRPC callback, SQS, a message bus other than RabbitMQ,
 * ...) means adding one more implementation + one more enum value -
 * JobDispatchService, the poller, and every public API are unaffected.
 */
public interface JobExecutor {

    ExecutionTarget supports();

    /** Deliver the job's payload to its target. Throw on any failure - JobDispatchService interprets any exception as a failed attempt. */
    void execute(Job job) throws Exception;
}
