package com.arijit.featureflagservice.controller;

import com.arijit.featureflagservice.config.FeatureFlagProperties;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.service.FlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Left open at the Spring Security filter-chain level (see SecurityConfig) and instead checks the
 * {@code token} query parameter itself, because scheduler-service's callback executor can't attach
 * a custom auth header per job - only a URL it POSTs to. Idempotent: FlagService#enable/disable
 * already no-op when the flag is already in the target state, so a scheduler retry after a timeout
 * (the call succeeded but the response was lost) is harmless.
 */
@RestController
@RequestMapping("/internal/flags")
@RequiredArgsConstructor
@Slf4j
public class FlagScheduleCallbackController {
    private final FlagService flagService;
    private final FeatureFlagProperties properties;

    @PostMapping("/{key}/scheduled-activate")
    public void scheduledActivate(@PathVariable String key,
                                   @RequestParam Environment environment,
                                   @RequestParam String token) {
        requireValidToken(token);
        log.info("scheduler-service callback: activating flag {}/{}", key, environment);
        flagService.applyScheduledActivation(key, environment);
    }

    @PostMapping("/{key}/scheduled-deactivate")
    public void scheduledDeactivate(@PathVariable String key,
                                     @RequestParam Environment environment,
                                     @RequestParam String token) {
        requireValidToken(token);
        log.info("scheduler-service callback: deactivating flag {}/{}", key, environment);
        flagService.applyScheduledDeactivation(key, environment);
    }

    private void requireValidToken(String token) {
        if (token == null || !token.equals(properties.internal().apiToken())) {
            throw new ApiException("Invalid internal callback token", HttpStatus.UNAUTHORIZED);
        }
    }
}
