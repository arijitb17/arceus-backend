package com.arijit.monitoringservice.dto;

import java.time.LocalDate;

public record UsageDayPoint(LocalDate day, String route, long requestCount, long errorCount) {}
