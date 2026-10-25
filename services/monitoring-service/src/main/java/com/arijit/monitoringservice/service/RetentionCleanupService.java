package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.repository.ServiceHealthSnapshotRepository;
import com.arijit.monitoringservice.repository.ServiceMetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionCleanupService {
    private final ServiceHealthSnapshotRepository healthRepository;
    private final ServiceMetricSnapshotRepository metricRepository;

    @Value("${arceus.monitoring.history.retention-days:30}")
    private int retentionDays;

    @Transactional
    @Scheduled(cron = "${arceus.monitoring.history.cleanup-cron:0 30 3 * * *}")
    public void cleanupOldSnapshots() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        healthRepository.deleteByCheckedAtBefore(cutoff);
        metricRepository.deleteByCapturedAtBefore(cutoff);
        log.info("Pruned health/metric history older than {} days", retentionDays);
    }
}
