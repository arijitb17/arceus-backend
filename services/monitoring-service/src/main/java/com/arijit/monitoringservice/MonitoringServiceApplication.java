package com.arijit.monitoringservice;

import com.arijit.monitoringservice.config.MonitoringProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Arceus Monitoring Service - the centralized
 * observability platform for the whole Arceus Platform. See this module's
 * pom.xml description for the full picture; {@link
 * com.arijit.monitoringservice.service.ServicePollingService} (driven by
 * {@code @EnableScheduling} below) is where the actual polling loop lives.
 */
@SpringBootApplication
@EnableConfigurationProperties(MonitoringProperties.class)
@EnableScheduling
public class MonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringServiceApplication.class, args);
    }
}
