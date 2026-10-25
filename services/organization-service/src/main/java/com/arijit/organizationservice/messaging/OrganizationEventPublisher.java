package com.arijit.organizationservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleType;
import com.arijit.organizationservice.entity.Organization;
import com.arijit.organizationservice.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String sourceService;

    public void organizationCreated(Organization org, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", org.getId().toString());
        payload.put("name", org.getName());
        payload.put("slug", org.getSlug());
        publish(ArceusEventsTopology.RK_ORGANIZATION_CREATED, "ORGANIZATION_CREATED", actorUserId, payload);
    }

    public void memberAdded(UUID organizationId, String addedUserId, String role, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", organizationId.toString());
        payload.put("userId", addedUserId.toString());
        payload.put("role", role);
        publish(ArceusEventsTopology.RK_ORGANIZATION_MEMBER_ADDED, "ORGANIZATION_MEMBER_ADDED", actorUserId, payload);
    }

    public void memberRemoved(UUID organizationId, String removedUserId, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", organizationId.toString());
        payload.put("userId", removedUserId.toString());
        publish(ArceusEventsTopology.RK_ORGANIZATION_MEMBER_REMOVED, "ORGANIZATION_MEMBER_REMOVED", actorUserId, payload);
    }

    public void projectCreated(Project project, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId().toString());
        payload.put("organizationId", project.getOrganizationId().toString());
        payload.put("name", project.getName());
        payload.put("slug", project.getSlug());
        publish(ArceusEventsTopology.RK_PROJECT_CREATED, "PROJECT_CREATED", actorUserId, payload);
    }

    public void projectDeleted(UUID projectId, UUID organizationId, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("organizationId", organizationId.toString());
        publish(ArceusEventsTopology.RK_PROJECT_DELETED, "PROJECT_DELETED", actorUserId, payload);
    }

    public void moduleEnableRequested(UUID projectId, EnvironmentType environment, ModuleType module, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("environment", environment.name());
        payload.put("module", module.name());
        publish(ArceusEventsTopology.RK_MODULE_ENABLE_REQUESTED, "MODULE_ENABLE_REQUESTED", actorUserId, payload);
    }

    public void moduleDisableRequested(UUID projectId, EnvironmentType environment, ModuleType module, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("environment", environment.name());
        payload.put("module", module.name());
        publish(ArceusEventsTopology.RK_MODULE_DISABLE_REQUESTED, "MODULE_DISABLE_REQUESTED", actorUserId, payload);
    }

    public void secretCreated(UUID projectId, EnvironmentType environment, String secretKey, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("environment", environment.name());
        payload.put("secretKey", secretKey);
        publish(ArceusEventsTopology.RK_SECRET_CREATED, "SECRET_CREATED", actorUserId, payload);
    }

    public void secretRevoked(UUID projectId, EnvironmentType environment, String secretKey, String actorUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("environment", environment.name());
        payload.put("secretKey", secretKey);
        publish(ArceusEventsTopology.RK_SECRET_REVOKED, "SECRET_REVOKED", actorUserId, payload);
    }

    private void publish(String routingKey, String eventType, String actorUserId, Map<String, Object> payload) {
        try {
            EventEnvelope envelope = EventEnvelope.of(
                    eventType,
                    Instant.now(),
                    sourceService,
                    null,
                    actorUserId == null ? null : actorUserId.toString(),
                    null,
                    null,
                    payload
            );
            rabbitTemplate.convertAndSend(ArceusEventsTopology.EVENTS_EXCHANGE, routingKey, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish {}: {}", eventType, e.getMessage());
        }
    }
}
