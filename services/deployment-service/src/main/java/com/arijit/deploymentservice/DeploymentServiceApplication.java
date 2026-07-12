package com.arijit.deploymentservice;

import com.arijit.deploymentservice.config.DeploymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Arceus Deployment Service - the centralized deployment management
 * platform for every Arceus service across every environment. See this
 * module's pom.xml for the elevator pitch, and {@link
 * com.arijit.deploymentservice.service.DeploymentService}'s Javadoc for
 * the full status machine create/approve/deploy/rollback/cancel drive a
 * {@link com.arijit.deploymentservice.entity.Deployment} through.
 */
@SpringBootApplication
@EnableConfigurationProperties(DeploymentProperties.class)
public class DeploymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeploymentServiceApplication.class, args);
    }
}
