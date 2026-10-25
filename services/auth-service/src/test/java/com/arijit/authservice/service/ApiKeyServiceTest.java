package com.arijit.authservice.service;

import com.arijit.arceus.events.domain.ApiKeyCreatedEvent;
import com.arijit.arceus.events.domain.ApiKeyRevokedEvent;
import com.arijit.arceus.grpc.organization.*;
import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.ApiKeyType;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.ApiKeyRepository;
import com.arijit.authservice.repository.UserRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock UserRepository userRepository;
    @Mock DomainEventPublisher domainEventPublisher;
    @Mock OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceBlockingStub orgStub;
    @Mock ApiKeyCryptoService crypto;
    @InjectMocks ApiKeyService service;

    private User owner;
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).name("Ash").email("ash@pallet.town").build();
    }

    private static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void stubOwner() {
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
    }

    private void allowManager(boolean authorized) {
        when(orgStub.checkManagerAccess(any())).thenReturn(
                CheckManagerAccessResponse.newBuilder().setAuthorized(authorized).build());
    }

    private void projectExists(boolean exists) {
        when(orgStub.checkProjectExists(any())).thenReturn(
                CheckProjectExistsResponse.newBuilder().setExists(exists).build());
    }

    private void echoSave() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            if (k.getId() == null) k.setId(UUID.randomUUID());
            return k;
        });
    }

    @Nested
    class PersonalKeys {

        @Test
        void createReturnsRawKeyAndStoresOnlyHashAndEncryptedCopy() throws Exception {
            stubOwner();
            echoSave();
            when(crypto.encrypt(anyString())).thenReturn("ENC");

            String raw = service.create(owner.getEmail(), "ci");

            assertThat(raw).startsWith("ak_").hasSizeGreaterThan(40);
            ArgumentCaptor<ApiKey> cap = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(cap.capture());
            ApiKey saved = cap.getValue();
            assertThat(saved.getKeyHash()).isEqualTo(sha256(raw));
            assertThat(saved.getEncryptedKey()).isEqualTo("ENC");
            assertThat(saved.getKeyPrefix()).isEqualTo(raw.substring(0, 8));
            assertThat(saved.getName()).isEqualTo("ci");
            assertThat(saved.getUser()).isSameAs(owner);
            assertThat(saved.getProjectId()).isNull();
            verify(crypto).encrypt(raw);
            verify(domainEventPublisher).publish(any(ApiKeyCreatedEvent.class));
        }

        @Test
        void createGeneratesUniqueKeys() {
            stubOwner();
            echoSave();
            when(crypto.encrypt(anyString())).thenReturn("E");

            assertThat(service.create(owner.getEmail(), "a")).isNotEqualTo(service.create(owner.getEmail(), "b"));
        }

        @Test
        void createUnknownUser() {
            when(userRepository.findByEmail("x@y.z")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create("x@y.z", "n"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            verifyNoInteractions(apiKeyRepository);
        }

        @Test
        void revealDecryptsStoredKey() {
            UUID id = UUID.randomUUID();
            stubOwner();
            when(apiKeyRepository.findByIdAndUser(id, owner)).thenReturn(Optional.of(
                    ApiKey.builder().id(id).encryptedKey("ENC").build()));
            when(crypto.decrypt("ENC")).thenReturn("ak_plain");

            assertThat(service.revealForOwner(owner.getEmail(), id)).isEqualTo("ak_plain");
        }

        @Test
        void revealLegacyKeyWithoutCiphertextIsGone() {
            UUID id = UUID.randomUUID();
            stubOwner();
            when(apiKeyRepository.findByIdAndUser(id, owner)).thenReturn(Optional.of(
                    ApiKey.builder().id(id).encryptedKey(null).build()));

            assertThatThrownBy(() -> service.revealForOwner(owner.getEmail(), id))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
            verifyNoInteractions(crypto);
        }

        @Test
        void revealNotOwnedIsNotFound() {
            UUID id = UUID.randomUUID();
            stubOwner();
            when(apiKeyRepository.findByIdAndUser(id, owner)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revealForOwner(owner.getEmail(), id))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void listReturnsOwnersKeys() {
            stubOwner();
            List<ApiKey> keys = List.of(ApiKey.builder().name("a").build());
            when(apiKeyRepository.findByUserOrderByCreatedAtDesc(owner)).thenReturn(keys);

            assertThat(service.listForOwner(owner.getEmail())).isSameAs(keys);
        }

        @Test
        void revokeMarksRevokedAndPublishes() {
            UUID id = UUID.randomUUID();
            ApiKey key = ApiKey.builder().id(id).build();
            stubOwner();
            when(apiKeyRepository.findByIdAndUser(id, owner)).thenReturn(Optional.of(key));

            service.revoke(owner.getEmail(), id);

            assertThat(key.isRevoked()).isTrue();
            verify(apiKeyRepository).save(key);
            verify(domainEventPublisher).publish(any(ApiKeyRevokedEvent.class));
        }

        @Test
        void revokeUnknownKey() {
            UUID id = UUID.randomUUID();
            stubOwner();
            when(apiKeyRepository.findByIdAndUser(id, owner)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revoke(owner.getEmail(), id)).isInstanceOf(ApiException.class);
            verify(apiKeyRepository, never()).save(any());
        }
    }

    @Nested
    class ProjectKeys {

        @Test
        void createSecretKeyUsesSkPrefixAndProjectMetadata() throws Exception {
            stubOwner();
            allowManager(true);
            projectExists(true);
            echoSave();
            when(crypto.encrypt(anyString())).thenReturn("ENC");

            String raw = service.createProjectKey(owner.getEmail(), orgId, projectId, "production",
                    ApiKeyType.SECRET, "backend", "read,write");

            assertThat(raw).startsWith("sk_");
            ArgumentCaptor<ApiKey> cap = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(cap.capture());
            ApiKey k = cap.getValue();
            assertThat(k.getOrganizationId()).isEqualTo(orgId);
            assertThat(k.getProjectId()).isEqualTo(projectId);
            assertThat(k.getEnvironment()).isEqualTo("production");
            assertThat(k.getKeyType()).isEqualTo(ApiKeyType.SECRET);
            assertThat(k.getScopes()).isEqualTo("read,write");
            assertThat(k.getKeyHash()).isEqualTo(sha256(raw));
        }

        @Test
        void createPublicKeyUsesPkPrefixAndBlankScopesBecomeNull() {
            stubOwner();
            allowManager(true);
            projectExists(true);
            echoSave();
            when(crypto.encrypt(anyString())).thenReturn("ENC");

            String raw = service.createProjectKey(owner.getEmail(), orgId, projectId, "dev",
                    ApiKeyType.PUBLIC, "sdk", "   ");

            assertThat(raw).startsWith("pk_");
            ArgumentCaptor<ApiKey> cap = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(cap.capture());
            assertThat(cap.getValue().getScopes()).isNull();
        }

        @Test
        void createRequiresManagerAccess() {
            stubOwner();
            allowManager(false);

            assertThatThrownBy(() -> service.createProjectKey(owner.getEmail(), orgId, projectId, "dev",
                    ApiKeyType.SECRET, "n", null))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            verifyNoInteractions(apiKeyRepository);
        }

        @Test
        void createFailsWhenProjectMissing() {
            stubOwner();
            allowManager(true);
            projectExists(false);

            assertThatThrownBy(() -> service.createProjectKey(owner.getEmail(), orgId, projectId, "dev",
                    ApiKeyType.SECRET, "n", null))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void organizationServiceOutageIs503() {
            stubOwner();
            when(orgStub.checkManagerAccess(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThatThrownBy(() -> service.createProjectKey(owner.getEmail(), orgId, projectId, "dev",
                    ApiKeyType.SECRET, "n", null))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }

        @Test
        void projectCheckOutageIs503() {
            stubOwner();
            allowManager(true);
            when(orgStub.checkProjectExists(any())).thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

            assertThatThrownBy(() -> service.createProjectKey(owner.getEmail(), orgId, projectId, "dev",
                    ApiKeyType.SECRET, "n", null))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }

        @Test
        void revealProjectKey() {
            UUID id = UUID.randomUUID();
            allowManager(true);
            when(apiKeyRepository.findByIdAndProjectId(id, projectId)).thenReturn(Optional.of(
                    ApiKey.builder().id(id).encryptedKey("E").build()));
            when(crypto.decrypt("E")).thenReturn("sk_x");

            assertThat(service.revealProjectKey("a@b.c", orgId, projectId, id)).isEqualTo("sk_x");
        }

        @Test
        void revealProjectKeyUnknown() {
            allowManager(true);
            when(apiKeyRepository.findByIdAndProjectId(any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revealProjectKey("a@b.c", orgId, projectId, UUID.randomUUID()))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void listForProjectRequiresAccessAndFiltersByEnvironment() {
            allowManager(true);
            List<ApiKey> keys = List.of(ApiKey.builder().build());
            when(apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, "dev")).thenReturn(keys);

            assertThat(service.listForProject("a@b.c", orgId, projectId, "dev")).isSameAs(keys);
        }

        @Test
        void listForProjectDeniedWithoutManagerRole() {
            allowManager(false);

            assertThatThrownBy(() -> service.listForProject("a@b.c", orgId, projectId, "dev"))
                    .isInstanceOf(ApiException.class);
            verifyNoInteractions(apiKeyRepository);
        }

        @Test
        void revokeProjectKeyPublishesWithKeyOwnerId() {
            UUID id = UUID.randomUUID();
            ApiKey key = ApiKey.builder().id(id).user(owner).build();
            allowManager(true);
            when(apiKeyRepository.findByIdAndProjectId(id, projectId)).thenReturn(Optional.of(key));

            service.revokeProjectKey("admin@b.c", orgId, projectId, id);

            assertThat(key.isRevoked()).isTrue();
            ArgumentCaptor<ApiKeyRevokedEvent> ev = ArgumentCaptor.forClass(ApiKeyRevokedEvent.class);
            verify(domainEventPublisher).publish(ev.capture());
            assertThat(ev.getValue().userId()).isEqualTo(owner.getId());
            assertThat(ev.getValue().email()).isEqualTo("admin@b.c");
        }

        @Test
        void getOrCreatePublicKeyReusesExistingActiveKeyWithoutExposingRaw() {
            ApiKey revoked = ApiKey.builder().keyType(ApiKeyType.PUBLIC).revoked(true).keyPrefix("pk_old00").build();
            ApiKey secret = ApiKey.builder().keyType(ApiKeyType.SECRET).keyPrefix("sk_aaaaa").build();
            ApiKey active = ApiKey.builder().keyType(ApiKeyType.PUBLIC).keyPrefix("pk_live0").build();
            allowManager(true);
            projectExists(true);
            when(apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, "prod"))
                    .thenReturn(List.of(revoked, secret, active));

            var result = service.getOrCreateProjectPublicKey(owner.getEmail(), orgId, projectId, "prod");

            assertThat(result.created()).isFalse();
            assertThat(result.rawKey()).isEmpty();
            assertThat(result.keyPrefix()).isEqualTo("pk_live0");
            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void getOrCreatePublicKeyMintsNewKeyWhenNoneActive() {
            allowManager(true);
            projectExists(true);
            stubOwner();
            echoSave();
            when(crypto.encrypt(anyString())).thenReturn("E");
            when(apiKeyRepository.findByProjectIdAndEnvironmentOrderByCreatedAtDesc(projectId, "prod"))
                    .thenReturn(List.of());

            var result = service.getOrCreateProjectPublicKey(owner.getEmail(), orgId, projectId, "prod");

            assertThat(result.created()).isTrue();
            assertThat(result.rawKey()).startsWith("pk_");
            assertThat(result.keyPrefix()).isEqualTo(result.rawKey().substring(0, 8));
            verify(domainEventPublisher).publish(any(ApiKeyCreatedEvent.class));
        }
    }

    @Nested
    class Validation {

        @Test
        void nullOrUnknownPrefixIsRejectedWithoutDbLookup() {
            assertThat(service.validateAndTouch(null)).isEmpty();
            assertThat(service.validateAndTouch("bearer-ish")).isEmpty();
            assertThat(service.validateAndTouch("")).isEmpty();

            verifyNoInteractions(apiKeyRepository);
        }

        @Test
        void activeKeyIsReturnedAndLastUsedUpdated() throws Exception {
            ApiKey key = ApiKey.builder().id(UUID.randomUUID()).build();
            when(apiKeyRepository.findByKeyHashFetchUser(sha256("ak_good"))).thenReturn(Optional.of(key));

            Optional<ApiKey> r = service.validateAndTouch("ak_good");

            assertThat(r).containsSame(key);
            assertThat(key.getLastUsedAt()).isBeforeOrEqualTo(Instant.now());
            verify(apiKeyRepository).save(key);
        }

        @Test
        void acceptsAllThreePrefixes() throws Exception {
            for (String raw : List.of("ak_1", "pk_1", "sk_1")) {
                when(apiKeyRepository.findByKeyHashFetchUser(sha256(raw)))
                        .thenReturn(Optional.of(ApiKey.builder().build()));
                assertThat(service.validateAndTouch(raw)).isPresent();
            }
        }

        @Test
        void revokedKeyIsRejectedAndNotTouched() throws Exception {
            ApiKey key = ApiKey.builder().revoked(true).build();
            when(apiKeyRepository.findByKeyHashFetchUser(sha256("ak_revoked"))).thenReturn(Optional.of(key));

            assertThat(service.validateAndTouch("ak_revoked")).isEmpty();

            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void unknownHashReturnsEmpty() {
            when(apiKeyRepository.findByKeyHashFetchUser(anyString())).thenReturn(Optional.empty());

            assertThat(service.validateAndTouch("sk_nothing")).isEmpty();
        }
    }
}
