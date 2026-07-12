package com.arijit.notificationservice.controller;

import com.arijit.notificationservice.scheduler.NotificationRetryScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The receiving end of scheduler-service's recurring "notification-retry"
 * job (registered by scheduler.RetryJobRegistrar) - scheduler-service's
 * HTTP_CALLBACK executor POSTs here on every fire. Left open at the Spring
 * Security filter-chain level (see SecurityConfig) and checks the
 * {@code token} query parameter itself instead, same reasoning as
 * deployment-service's DeploymentCallbackController: scheduler-service's
 * callback executor can't attach a custom auth header per job, only a URL
 * it POSTs to.
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@Slf4j
public class RetryCallbackController {

    private final NotificationRetryScheduler retryScheduler;

    @Value("${arceus.notification.internal.api-token}")
    private String internalApiToken;

    @PostMapping("/retry-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryFailed(@RequestParam String token) {
        if (token == null || !token.equals(internalApiToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal callback token");
        }
        retryScheduler.retryFailedNotifications();
    }
}
