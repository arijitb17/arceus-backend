package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.dto.CreateJobRequest;
import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.JobTriggerType;
import com.arijit.schedulerservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobValidationServiceTest {
    private final JobValidationService validator = new JobValidationService();

    private CreateJobRequest request(JobTriggerType triggerType, String cron, Long delayMs, Long fixedRateMs, Long fixedDelayMs,
                                      ExecutionTarget target, String callbackUrl, String exchange, String routingKey) {
        return new CreateJobRequest(
                "job", "service", triggerType, cron, delayMs, fixedRateMs, fixedDelayMs,
                target, callbackUrl, exchange, routingKey, "{}", 0, null, null, null, null, null, null
        );
    }

    @Test
    void validCronHttpCallbackJobPasses() {
        var req = request(JobTriggerType.CRON, "0 0 * * * *", null, null, null,
                ExecutionTarget.HTTP_CALLBACK, "http://example.com/callback", null, null);
        assertDoesNotThrow(() -> validator.validateCreate(req));
    }

    @Test
    void cronJobWithoutCronExpressionIsRejected() {
        var req = request(JobTriggerType.CRON, null, null, null, null,
                ExecutionTarget.HTTP_CALLBACK, "http://example.com", null, null);
        assertThrows(ApiException.class, () -> validator.validateCreate(req));
    }

    @Test
    void delayJobWithoutDelayMsIsRejected() {
        var req = request(JobTriggerType.DELAY, null, null, null, null,
                ExecutionTarget.HTTP_CALLBACK, "http://example.com", null, null);
        assertThrows(ApiException.class, () -> validator.validateCreate(req));
    }

    @Test
    void httpCallbackWithoutUrlIsRejected() {
        var req = request(JobTriggerType.IMMEDIATE, null, null, null, null,
                ExecutionTarget.HTTP_CALLBACK, null, null, null);
        assertThrows(ApiException.class, () -> validator.validateCreate(req));
    }

    @Test
    void httpCallbackWithNonHttpUrlIsRejected() {
        var req = request(JobTriggerType.IMMEDIATE, null, null, null, null,
                ExecutionTarget.HTTP_CALLBACK, "ftp://example.com", null, null);
        assertThrows(ApiException.class, () -> validator.validateCreate(req));
    }

    @Test
    void messagePublishWithoutExchangeOrRoutingKeyIsRejected() {
        var req = request(JobTriggerType.IMMEDIATE, null, null, null, null,
                ExecutionTarget.MESSAGE_PUBLISH, null, null, null);
        assertThrows(ApiException.class, () -> validator.validateCreate(req));
    }

    @Test
    void validMessagePublishJobPasses() {
        var req = request(JobTriggerType.IMMEDIATE, null, null, null, null,
                ExecutionTarget.MESSAGE_PUBLISH, null, "some.exchange", "some.key");
        assertDoesNotThrow(() -> validator.validateCreate(req));
    }
}
