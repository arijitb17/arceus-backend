package com.arijit.organizationservice.messaging;

import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.organizationservice.entity.GithubConnection;
import com.arijit.organizationservice.repository.GithubConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * See GithubConnection's javadoc and RabbitMQConfig's GitHub-sync queue
 * comment: deployment-service owns the real GithubRepoConnection (and the
 * GitHub App installation detail behind it); this just mirrors enough of
 * it here for the Developer Portal to show connection status without a
 * cross-service call on every page load.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GithubConnectionSyncConsumer {

    private final GithubConnectionRepository repository;

    @RabbitListener(queues = "arceus.organization.github-sync.queue")
    public void onGithubEvent(EventEnvelope envelope) {
        Object projectIdObj = envelope.payload().get("projectId");
        if (projectIdObj == null) {
            log.error("Malformed GitHub sync event, missing projectId: {}", envelope.eventType());
            return;
        }

        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdObj.toString());
        } catch (IllegalArgumentException e) {
            log.error("Malformed GitHub sync event, could not parse projectId: {}", envelope.payload());
            return;
        }

        switch (envelope.eventType()) {
            case "GITHUB_REPO_CONNECTED" -> {
                Object repoFullNameObj = envelope.payload().get("repoFullName");
                Object defaultBranchObj = envelope.payload().get("defaultBranch");
                Object installationIdObj = envelope.payload().get("installationId");
                if (repoFullNameObj == null || defaultBranchObj == null || installationIdObj == null) {
                    log.error("Malformed GITHUB_REPO_CONNECTED event: {}", envelope.payload());
                    return;
                }

                repository.findByProjectIdAndDisconnectedAtIsNull(projectId).ifPresent(existing -> {
                    existing.setDisconnectedAt(Instant.now());
                    repository.save(existing);
                });

                repository.save(GithubConnection.builder()
                        .projectId(projectId)
                        .repoFullName(repoFullNameObj.toString())
                        .defaultBranch(defaultBranchObj.toString())
                        .installationId(UUID.fromString(installationIdObj.toString()))
                        .connectedBy(envelope.userId() == null ? "" : envelope.userId())
                        .connectedAt(Instant.now())
                        .build());
                log.info("Synced GitHub connection for project={}: {}", projectId, repoFullNameObj);
            }
            case "GITHUB_REPO_DISCONNECTED" -> repository.findByProjectIdAndDisconnectedAtIsNull(projectId).ifPresentOrElse(
                    existing -> {
                        existing.setDisconnectedAt(Instant.now());
                        repository.save(existing);
                        log.info("Synced GitHub disconnection for project={}", projectId);
                    },
                    () -> log.warn("Received GITHUB_REPO_DISCONNECTED for project={} with no active connection to sync", projectId)
            );
            default -> log.warn("Unexpected event type {} on GitHub sync queue, ignoring", envelope.eventType());
        }
    }
}
