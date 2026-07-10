package com.arijit.schedulerservice;

import com.arijit.schedulerservice.config.SchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arceus Scheduler Service - see the module Javadoc in pom.xml for the
 * elevator pitch. {@code @EnableScheduling} backs JobPollerService's
 * {@code @Scheduled} poll loop (today's single-node "engine"); this is the
 * one seam a future distributed/leader-elected poller would replace
 * without touching anything else. {@code @EnableAsync} backs the bounded
 * worker pool jobs actually execute on (see AsyncConfig), so the poll loop
 * itself never blocks on a slow HTTP callback or RabbitMQ publish.
 */
@SpringBootApplication
@EnableConfigurationProperties(SchedulerProperties.class)
@EnableScheduling
@EnableAsync
public class SchedulerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulerServiceApplication.class, args);
    }
}
