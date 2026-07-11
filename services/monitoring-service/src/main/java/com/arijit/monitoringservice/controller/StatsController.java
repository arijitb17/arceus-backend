package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.StatsResponse;
import com.arijit.monitoringservice.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /stats - Platform Statistics: a lighter-weight numeric summary than /dashboard. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Stats", description = "Platform Statistics")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/stats")
    @Operation(summary = "Platform-wide numeric statistics")
    public StatsResponse stats() {
        return statsService.getStats();
    }
}
