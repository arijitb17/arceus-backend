package com.arijit.authservice.service;

import com.arijit.arceus.events.domain.ApiKeyCreatedEvent;
import com.arijit.arceus.events.domain.ApiKeyRevokedEvent;
import com.arijit.arceus.grpc.organization.CheckManagerAccessRequest;
import com.arijit.arceus.grpc.organization.CheckManagerAccessResponse;
import com.arijit.arceus.grpc.organization.CheckProjectExistsRequest;
import com.arijit.arceus.grpc.organization.CheckProjectExistsResponse;
import com.arijit.arceus.grpc.organization.OrganizationAccessGrpcServiceGrpc;
import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.ApiKeyType;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.ApiKeyRepository;
import com.arijit.authservice.repository.UserRepository;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private static final String PLATFORM_KEY_PREFIX = "ak_";
    private static final String PUBLIC_KEY_PREFIX = "pk_";
    private static final String SECRET_KEY_PREFIX = "sk_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceBlockingStub organizationAccessStub;
    private final ApiKeyCryptoService apiKeyCryptoService;

    @Transactional
    public String create(String ownerEmail, String name) {
        User owner = requireUser(ownerEmail);
        String rawKey = generateRawKey(PLATFORM_KEY_PREFIX);

        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .user(owner)
                .name(name)
                .keyHash(sha256Hex(rawKey))
                .encryptedKey(apiKeyCryptoService.encrypt(rawKey))
                .keyPrefix(rawKey.substring(0, 8))
                .build());

        log.info("API key created: userId={} keyId={} prefix={}", owner.getId(), apiKey.getId(), apiKey.getKeyPrefix());
        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), name));
        return rawKey;
    }

    public String revealForOwner(String ownerEmail, UUID apiKeyId) {
        User owner = requireUser(ownerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndUser(apiKeyId, owner)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId, HttpStatus.NOT_FOUND));
        log.info("API key revealed: userId={} keyId={}", owner.getId(), apiKeyId);
        return decryptOrThrow(apiKey);
    }

    private String decryptOrThrow(ApiKey apiKey) {
        if (apiKey.getEncryptedKey() == null) {
            log.warn("API key reveal rejected: keyId={} has no retained encrypted key", apiKey.getId());
            // Created before AUTH_API_KEY_ENCRYPTION_KEY existed - nothing to decrypt, the raw key
            // was genuinely never retained for this one. Not an error state, just an older row.
            throw new ApiException("This key was created before reveal was supported - it can't be shown again. Revoke it and create a new one.", HttpStatus.GONE);
        }
        return apiKeyCryptoService.decrypt(apiKey.getEncryptedKey());
    }

    public List<ApiKey> listForOwner(String ownerEmail) {
        return apiKeyRepository.findByUserOrderByCreatedAtDesc(requireUser(ownerEmail));
    }

    @Transactional
    public void revoke(String ownerEmail, UUID apiKeyId) {
        User owner = requireUser(ownerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndUser(apiKeyId, owner)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId, HttpStatus.NOT_FOUND));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
        log.info("API key revoked: userId={} keyId={}", owner.getId(), apiKeyId);
        domainEventPublisher.publish(ApiKeyRevokedEvent.now(owner.getId(), owner.getEmail(), apiKeyId));
    }

    @Transactional
    public String createProjectKey(String ownerEmail, UUID organizationId, UUID projectId, String environment,
                                    ApiKeyType keyType, String name, String scopes) {
        User owner = requireUser(ownerEmail);
        requireManagerAccess(organizationId, ownerEmail);
        requireProjectExists(organizationId, projectId);

        String prefix = keyType == ApiKeyType.PUBLIC ? PUBLIC_KEY_PREFIX : SECRET_KEY_PREFIX;
        String rawKey = generateRawKey(prefix);

        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .user(owner)
                .name(name)
                .keyHash(sha256Hex(rawKey))
                .encryptedKey(apiKeyCryptoService.encrypt(rawKey))
                .keyPrefix(rawKey.substring(0, 8))
                .organizationId(organizationId)
                .projectId(projectId)
                .environment(environment)
                .keyType(keyType)
                .scopes(scopes == null || scopes.isBlank() ? null : scopes)
                .build());

        log.info("Project API key created: keyId={} type={} prefix={} org={} project={} env={} by={}",
                apiKey.getId(), keyType, apiKey.getKeyPrefix(), organizationId, projectId, environment, owner.getId());
        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), name));
        return rawKey;
    }

    public String revealProjectKey(String callerEmail, UUID organizationId, UUID projectId, UUID apiKeyId) {
        requireManagerAccess(organizationId, callerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndProjectId(apiKeyId, projectId)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId + " for this project", HttpStatus.NOT_FOUND));
        log.info("Project API key revealed: keyId={} project={} org={}", apiKeyId, projectId, organizationId);
        return decryptOrThrow(apiKey);
    }

    public List<ApiKey> listForProject(String callerEmail, UUID organizationId, UUID projectId, String environment) {
        requireManagerAccess(organizationId, callerEmail);
        return apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, environment);
    }

    @Transactional
    public void revokeProjectKey(String callerEmail, UUID organizationId, UUID projectId, UUID apiKeyId) {
        requireManagerAccess(organizationId, callerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndProjectId(apiKeyId, projectId)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId + " for this project", HttpStatus.NOT_FOUND));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
        log.info("Project API key revoked: keyId={} project={} org={}", apiKeyId, projectId, organizationId);
        domainEventPublisher.publish(ApiKeyRevokedEvent.now(apiKey.getUser().getId(), callerEmail, apiKeyId));
    }

    @Transactional
    public ProjectPublicKeyResult getOrCreateProjectPublicKey(String requestedByEmail, UUID organizationId, UUID projectId, String environment) {
        requireManagerAccess(organizationId, requestedByEmail);
        requireProjectExists(organizationId, projectId);

        Optional<ApiKey> existing = apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, environment).stream()
                .filter(k -> !k.isRevoked() && k.getKeyType() == ApiKeyType.PUBLIC)
                .findFirst();

        if (existing.isPresent()) {
            log.debug("Reusing existing PUBLIC key: project={} env={} prefix={}", projectId, environment, existing.get().getKeyPrefix());
            return new ProjectPublicKeyResult(false, "", existing.get().getKeyPrefix());
        }

        User owner = requireUser(requestedByEmail);
        String rawKey = generateRawKey(PUBLIC_KEY_PREFIX);
        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .user(owner)
                .name("Generated for SDK config")
                .keyHash(sha256Hex(rawKey))
                .encryptedKey(apiKeyCryptoService.encrypt(rawKey))
                .keyPrefix(rawKey.substring(0, 8))
                .organizationId(organizationId)
                .projectId(projectId)
                .environment(environment)
                .keyType(ApiKeyType.PUBLIC)
                .build());

        log.info("PUBLIC SDK key minted: keyId={} prefix={} project={} env={}", apiKey.getId(), apiKey.getKeyPrefix(), projectId, environment);
        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), apiKey.getName()));
        return new ProjectPublicKeyResult(true, rawKey, apiKey.getKeyPrefix());
    }

    public record ProjectPublicKeyResult(boolean created, String rawKey, String keyPrefix) {}

    private void requireManagerAccess(UUID organizationId, String callerEmail) {
        CheckManagerAccessResponse response;
        try {
            response = organizationAccessStub.checkManagerAccess(CheckManagerAccessRequest.newBuilder()
                    .setOrganizationId(organizationId.toString())
                    .setUserEmail(callerEmail)
                    .build());
        } catch (StatusRuntimeException e) {
            log.error("organization-service checkManagerAccess failed: org={} status={}", organizationId, e.getStatus(), e);
            throw new ApiException("Could not verify organization access (organization-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!response.getAuthorized()) {
            log.warn("Manager access denied: org={} user={}", organizationId, LogMasker.email(callerEmail));
            throw new ApiException("Requires OWNER or ADMIN role in this organization", HttpStatus.FORBIDDEN);
        }
    }

    private void requireProjectExists(UUID organizationId, UUID projectId) {
        CheckProjectExistsResponse response;
        try {
            response = organizationAccessStub.checkProjectExists(CheckProjectExistsRequest.newBuilder()
                    .setOrganizationId(organizationId.toString())
                    .setProjectId(projectId.toString())
                    .build());
        } catch (StatusRuntimeException e) {
            log.error("organization-service checkProjectExists failed: org={} project={} status={}", organizationId, projectId, e.getStatus(), e);
            throw new ApiException("Could not verify project (organization-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!response.getExists()) {
            log.warn("Project not found: org={} project={}", organizationId, projectId);
            throw new ApiException("No project " + projectId + " in organization " + organizationId, HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public Optional<ApiKey> validateAndTouch(String rawKey) {
        if (rawKey == null || (!rawKey.startsWith(PLATFORM_KEY_PREFIX)
                && !rawKey.startsWith(PUBLIC_KEY_PREFIX) && !rawKey.startsWith(SECRET_KEY_PREFIX))) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHashFetchUser(sha256Hex(rawKey))
                .filter(k -> !k.isRevoked())
                .map(k -> {
                    k.setLastUsedAt(Instant.now());
                    apiKeyRepository.save(k);
                    return k;
                });
    }

    private String generateRawKey(String prefix) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User lookup failed: email={}", LogMasker.email(email));
                    return new ApiException("No user found with email: " + email, HttpStatus.NOT_FOUND);
                });
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
