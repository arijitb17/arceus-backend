package com.arijit.authservice.controller;

import com.arijit.authservice.scheduler.SessionCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The receiving end of scheduler-service's recurring "auth-session-cleanup"
 * job (registered by scheduler.SessionCleanupJobRegistrar) - scheduler-service's
 * HTTP_CALLBACK executor POSTs here on every fire. Listed in
 * SecurityConfig.PUBLIC_ENDPOINTS and checks its own {@code token} query
 * parameter instead, same reasoning as every other internal callback
 * controller on the platform: scheduler-service's callback executor can't
 * attach a custom auth header per job, only a URL it POSTs to.
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class SessionCleanupCallbackController {

    private final SessionCleanupService sessionCleanupService;

    @Value("${app.internal.api-token}")
    private String internalApiToken;

    @PostMapping("/sessions/clean-expired")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cleanExpiredSessions(@RequestParam String token) {
        if (token == null || !token.equals(internalApiToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal callback token");
        }
        sessionCleanupService.cleanExpiredSessions();
    }
}
