package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobPollerService {
    private final JobDispatchService dispatchService;
    private final SchedulerProperties properties;

    @Scheduled(fixedDelayString = "${arceus.scheduler.poll.interval-ms:1000}")
    public void poll() {
        try {
            Instant now = Instant.now();
            Pageable pageable = PageRequest.of(0, properties.poll().batchSize());
            List<UUID> claimed = dispatchService.claimDueJobs(now, pageable);
            if (!claimed.isEmpty()) {
                log.debug("Poller claimed {} due job(s)", claimed.size());
                claimed.forEach(dispatchService::executeClaimed);
            }
        } catch (Exception e) {
            // The poll loop itself must never die - a single bad tick (e.g. a transient DB blip)
            // should not stop every future job in the platform from ever firing again.
            log.error("Job poll tick failed, will retry next interval", e);
        }
    }
}
