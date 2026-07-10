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

/**
 * The one seam a future distributed/leader-elected poller replaces
 * without touching JobService, the REST/gRPC APIs, or anything else - see
 * the module Javadoc in pom.xml. Every {@code arceus.scheduler.poll.interval-ms}
 * (default 1s), claim whatever's due (bounded to one batch) and hand each
 * off to the worker pool; never blocks on the jobs it dispatches.
 */
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
            // The poll loop itself must never die - a single bad tick
            // (e.g. a transient DB blip) should not stop every future
            // job in the platform from ever firing again.
            log.error("Job poll tick failed, will retry next interval", e);
        }
    }
}
