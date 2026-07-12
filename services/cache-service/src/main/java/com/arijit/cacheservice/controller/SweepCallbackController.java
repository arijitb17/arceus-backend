package com.arijit.cacheservice.controller;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.service.CacheSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The receiving end of scheduler-service's recurring "cache-sweep" job
 * (registered by scheduler.SweepJobRegistrar) - scheduler-service's
 * HTTP_CALLBACK executor POSTs here on every fire.
 * <p>
 * cache-service has no Spring Security filter chain at all (it's assumed
 * reachable only from inside the platform's own network, same as Redis
 * itself), but this still checks a shared-secret {@code token} query
 * parameter - same defense-in-depth reasoning as every other internal
 * callback controller on the platform: this endpoint triggers a
 * destructive, scan-the-whole-keyspace operation, so it shouldn't fire for
 * literally anyone who can reach the port.
 */
@RestController
@RequestMapping("/internal/cache")
@RequiredArgsConstructor
public class SweepCallbackController {

    private final CacheSweepService sweepService;
    private final CacheProperties properties;

    @PostMapping("/sweep")
    public void sweep(@RequestParam String token) {
        if (token == null || !token.equals(properties.internal().apiToken())) {
            throw new ApiException("Invalid internal callback token", HttpStatus.UNAUTHORIZED);
        }
        sweepService.sweepStaleNoExpiryEntries();
    }
}
