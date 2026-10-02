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

    /**
     * @return the raw key - shown directly in the creation response, but
     *         (unlike a pure hash-only design) also retrievable again later
     *         via {@link #revealForOwner} - see ApiKey's javadoc for why.
     */
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

        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), name));
        return rawKey;
    }

    /** @return the decrypted raw key - see ApiKey's javadoc for the trade-off this represents. */
    public String revealForOwner(String ownerEmail, UUID apiKeyId) {
        User owner = requireUser(ownerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndUser(apiKeyId, owner)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId, HttpStatus.NOT_FOUND));
        return decryptOrThrow(apiKey);
    }

    private String decryptOrThrow(ApiKey apiKey) {
        if (apiKey.getEncryptedKey() == null) {
            // Created before AUTH_API_KEY_ENCRYPTION_KEY existed - nothing
            // to decrypt, the raw key was genuinely never retained for
            // this one. Not an error state, just an older row.
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
        domainEventPublisher.publish(ApiKeyRevokedEvent.now(owner.getId(), owner.getEmail(), apiKeyId));
    }

    // ---------- Project-scoped keys (architecture design §7) ----------

    /**
     * Issues a key scoped to one project+environment, for the Developer
     * Portal's own key-management screen - distinct from {@link #create},
     * which is the pre-existing self-service platform-level flow. Callers
     * must be an OWNER/ADMIN of {@code organizationId}, checked via
     * organization-service's own OrgRole model over gRPC (see
     * OrganizationAccessGrpcService) rather than duplicated here.
     *
     * @return the raw key - see {@link #create}'s javadoc for why this is the only time it's available.
     */
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

        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), name));
        return rawKey;
    }

    /** @return the decrypted raw key - see ApiKey's javadoc for the trade-off this represents. */
    public String revealProjectKey(String callerEmail, UUID organizationId, UUID projectId, UUID apiKeyId) {
        requireManagerAccess(organizationId, callerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndProjectId(apiKeyId, projectId)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId + " for this project", HttpStatus.NOT_FOUND));
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
        domainEventPublisher.publish(ApiKeyRevokedEvent.now(apiKey.getUser().getId(), callerEmail, apiKeyId));
    }

    /**
     * Idempotent PUBLIC key provisioning for SDK config generation
     * (architecture design §9), called by organization-service via
     * ApiKeyProvisioningGrpcService rather than the REST endpoints above,
     * since the caller there is another service, not a browser request. If
     * an active PUBLIC key already exists for this project+environment, it
     * is returned as "not newly created" with no raw key attached - this
     * method's caller only ever needs "does one exist", not its value;
     * fetch it via {@link #revealProjectKey} separately if actually needed.
     *
     * @return {@code [true, rawKey, prefix]} if a new key was minted, or {@code [false, "", prefix]} if one already existed
     */
    @Transactional
    public ProjectPublicKeyResult getOrCreateProjectPublicKey(String requestedByEmail, UUID organizationId, UUID projectId, String environment) {
        requireManagerAccess(organizationId, requestedByEmail);
        requireProjectExists(organizationId, projectId);

        Optional<ApiKey> existing = apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, environment).stream()
                .filter(k -> !k.isRevoked() && k.getKeyType() == ApiKeyType.PUBLIC)
                .findFirst();

        if (existing.isPresent()) {
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
            throw new ApiException("Could not verify organization access (organization-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!response.getAuthorized()) {
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
            throw new ApiException("Could not verify project (organization-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!response.getExists()) {
            throw new ApiException("No project " + projectId + " in organization " + organizationId, HttpStatus.NOT_FOUND);
        }
    }

    // ---------- Shared ----------

    /**
     * Validates a raw key presented in an X-API-Key header and, if valid,
     * updates its lastUsedAt timestamp. Returns empty for anything wrong -
     * unknown hash, revoked - so ApiKeyAuthFilter can treat every failure
     * mode the same way (fall through unauthenticated). Recognizes all
     * three prefixes (platform {@code ak_}, project-scoped {@code pk_}/
     * {@code sk_}) - the caller (ApiKeyAuthFilter) doesn't need to know
     * which kind it got; the resolved ApiKey row carries that.
     */
    @Transactional
    public Optional<ApiKey> validateAndTouch(String rawKey) {
        if (rawKey == null || (!rawKey.startsWith(PLATFORM_KEY_PREFIX)
                && !rawKey.startsWith(PUBLIC_KEY_PREFIX) && !rawKey.startsWith(SECRET_KEY_PREFIX))) {
            return Optional.empty();
        }
        // findByKeyHashFetchUser, not findByKeyHash: the caller
        // (ApiKeyProvisioningGrpcService.validateApiKey) reads
        // apiKey.getUser() after this transaction has already closed - see
        // that query method's javadoc for why the plain lazy lookup breaks
        // there with a LazyInitializationException.
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
                .orElseThrow(() -> new ApiException("No user found with email: " + email, HttpStatus.NOT_FOUND));
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
            // SHA-256 is guaranteed available on every JVM - this can't actually happen.
            throw new IllegalStateException(e);
        }
    }
}
