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
