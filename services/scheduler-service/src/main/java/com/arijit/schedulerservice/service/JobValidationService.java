package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.dto.CreateJobRequest;
import com.arijit.schedulerservice.entity.JobTriggerType;
import com.arijit.schedulerservice.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JobValidationService {
    public void validateCreate(CreateJobRequest request) {
        switch (request.triggerType()) {
            case CRON -> requireBlank(request.cronExpression(), "cronExpression", JobTriggerType.CRON);
            case DELAY -> requirePositive(request.delayMs(), "delayMs", JobTriggerType.DELAY);
            case FIXED_RATE -> requirePositive(request.fixedRateMs(), "fixedRateMs", JobTriggerType.FIXED_RATE);
            case FIXED_DELAY -> requirePositive(request.fixedDelayMs(), "fixedDelayMs", JobTriggerType.FIXED_DELAY);
            case IMMEDIATE -> { }
        }

        switch (request.executionTarget()) {
            case HTTP_CALLBACK -> {
                if (isBlank(request.callbackUrl())) {
                    throw badRequest("callbackUrl is required for HTTP_CALLBACK jobs");
                }
                if (!request.callbackUrl().startsWith("http://") && !request.callbackUrl().startsWith("https://")) {
                    throw badRequest("callbackUrl must be an absolute http(s) URL");
                }
            }
            case MESSAGE_PUBLISH -> {
                if (isBlank(request.targetExchange()) || isBlank(request.targetRoutingKey())) {
                    throw badRequest("targetExchange and targetRoutingKey are both required for MESSAGE_PUBLISH jobs");
                }
            }
        }

        if (request.maxRetries() != null && request.maxRetries() < 0) {
            throw badRequest("maxRetries cannot be negative");
        }
        if (request.retryDelayMs() != null && request.retryDelayMs() < 0) {
            throw badRequest("retryDelayMs cannot be negative");
        }
        if (request.backoffMultiplier() != null && request.backoffMultiplier() < 1.0) {
            throw badRequest("backoffMultiplier must be >= 1.0");
        }
    }

    private void requireBlank(String value, String field, JobTriggerType triggerType) {
        if (isBlank(value)) {
            throw badRequest(field + " is required for " + triggerType + " jobs");
        }
    }

    private void requirePositive(Long value, String field, JobTriggerType triggerType) {
        if (value == null || value <= 0) {
            throw badRequest(field + " is required and must be positive for " + triggerType + " jobs");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ApiException badRequest(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
