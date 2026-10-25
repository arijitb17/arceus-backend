package com.arijit.organizationservice.service;

import com.arijit.organizationservice.dto.CreateSecretRequest;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.Secret;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecretService {
    private final SecretRepository repository;
    private final OrgAccessGuard accessGuard;
    private final ProjectService projectService;
    private final SecretCryptoService cryptoService;
    private final OrganizationEventPublisher eventPublisher;

    public List<Secret> list(UUID organizationId, UUID projectId, EnvironmentType environment, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);
        return repository.findByProjectIdAndEnvironmentAndRevokedFalse(projectId, environment);
    }

    @Transactional
    public Secret create(UUID organizationId, UUID projectId, EnvironmentType environment,
                          CreateSecretRequest request, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);

        repository.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, environment, request.secretKey())
                .ifPresent(existing -> {
                    log.warn("Secret create rejected: key '{}' already exists project={} env={}", request.secretKey(), projectId, environment);
                    throw new ApiException("Secret '" + request.secretKey() + "' already exists for this environment - revoke it first to replace it", HttpStatus.CONFLICT);
                });

        Secret secret = repository.save(Secret.builder()
                .projectId(projectId)
                .environment(environment)
                .secretKey(request.secretKey())
                .encryptedValue(cryptoService.encrypt(request.value()))
                .encryptionKeyVersion(cryptoService.currentKeyVersion())
                .version(1)
                .createdBy(callerUserId)
                .createdAt(Instant.now())
                .revoked(false)
                .build());

        log.info("Secret created: key={} project={} env={} by={}", request.secretKey(), projectId, environment, callerUserId);
        eventPublisher.secretCreated(projectId, environment, request.secretKey(), callerUserId);
        return secret;
    }

    @Transactional
    public void revoke(UUID organizationId, UUID projectId, EnvironmentType environment, String secretKey, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);

        Secret secret = repository.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, environment, secretKey)
                .orElseThrow(() -> {
                    log.warn("Secret revoke rejected: key '{}' not found project={} env={}", secretKey, projectId, environment);
                    return new ApiException("Secret '" + secretKey + "' not found for this environment", HttpStatus.NOT_FOUND);
                });

        secret.setRevoked(true);
        secret.setRevokedAt(Instant.now());
        repository.save(secret);

        log.info("Secret revoked: key={} project={} env={} by={}", secretKey, projectId, environment, callerUserId);
        eventPublisher.secretRevoked(projectId, environment, secretKey, callerUserId);
    }

    public String resolvePlaintext(UUID projectId, EnvironmentType environment, String secretKey) {
        Secret secret = repository.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, environment, secretKey)
                .orElseThrow(() -> {
                    log.warn("Secret resolve failed: key '{}' not found project={} env={}", secretKey, projectId, environment);
                    return new ApiException("Secret '" + secretKey + "' not found for this environment", HttpStatus.NOT_FOUND);
                });
        log.debug("Secret resolved for server-side use: key={} project={} env={}", secretKey, projectId, environment);
        return cryptoService.decrypt(secret.getEncryptedValue());
    }
}
