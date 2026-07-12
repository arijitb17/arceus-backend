package com.arijit.notificationservice.dto;

public record QueueSummaryResponse(
        long pending,
        long retrying,
        long dead,
        long sent,
        long failed,
        long suppressed
) {
}
