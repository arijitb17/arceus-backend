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
