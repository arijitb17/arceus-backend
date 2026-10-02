package com.arijit.organizationservice.service;

import com.arijit.organizationservice.dto.CreateSecretRequest;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.Secret;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * See Secret's javadoc for the retention/encryption model.
 * {@link #resolvePlaintext} is deliberately the only method that ever
 * decrypts a value, and it is not exposed through SecretController - it's
 * meant for server-side consumers that need the plaintext at the point of
 * use (e.g. handing a project's secrets to its own services at runtime).
 * No such consumer exists yet in this platform - secrets can be created,
 * listed (metadata only), and revoked today, but nothing currently calls
 * resolvePlaintext.
 */
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

        eventPublisher.secretCreated(projectId, environment, request.secretKey(), callerUserId);
        return secret;
    }

    @Transactional
    public void revoke(UUID organizationId, UUID projectId, EnvironmentType environment, String secretKey, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);

        Secret secret = repository.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, environment, secretKey)
                .orElseThrow(() -> new ApiException("Secret '" + secretKey + "' not found for this environment", HttpStatus.NOT_FOUND));

        secret.setRevoked(true);
        secret.setRevokedAt(Instant.now());
        repository.save(secret);

        eventPublisher.secretRevoked(projectId, environment, secretKey, callerUserId);
    }

    /** Server-side only - see class javadoc. Not reachable from any controller. */
    public String resolvePlaintext(UUID projectId, EnvironmentType environment, String secretKey) {
        Secret secret = repository.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, environment, secretKey)
                .orElseThrow(() -> new ApiException("Secret '" + secretKey + "' not found for this environment", HttpStatus.NOT_FOUND));
        return cryptoService.decrypt(secret.getEncryptedValue());
    }
}
