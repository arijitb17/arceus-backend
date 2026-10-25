package com.arijit.organizationservice.service;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyRequest;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyResponse;
import com.arijit.arceus.grpc.tenant.*;
import com.arijit.organizationservice.dto.SdkConfigResponse;
import com.arijit.organizationservice.entity.*;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.ModuleEnablementRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleAndSdkServicesTest {

    @Mock ModuleEnablementRepository repo;
    @Mock OrgAccessGuard guard;
    @Mock ProjectService projectService;
    @Mock OrganizationEventPublisher publisher;
    @Mock ModuleProvisioningClient provisioning;

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private static final EnvironmentType ENV = EnvironmentType.STAGING;

    private void assertApi(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Nested
    class Enablement {

        private ModuleEnablementService service;

        @BeforeEach
        void setUp() {
            service = new ModuleEnablementService(repo, guard, projectService, publisher, provisioning);
            lenient().when(repo.save(any(ModuleEnablement.class))).thenAnswer(i -> i.getArgument(0));
        }

        private ModuleEnablement existing(ModuleStatus status) {
            return ModuleEnablement.builder().projectId(projectId).environment(ENV).module(ModuleType.CACHE).status(status).build();
        }

        @Test
        void listRequiresMembership() {
            when(repo.findByProjectIdAndEnvironment(projectId, ENV)).thenReturn(List.of(existing(ModuleStatus.ACTIVE)));

            assertThat(service.list(orgId, projectId, ENV, "u")).hasSize(1);
            verify(guard).requireMember(orgId, "u");
        }

        @Test
        void enableNewModuleProvisionsAndEndsActive() {
            ModuleEnablement[] stored = new ModuleEnablement[1];
            when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE))
                    .thenAnswer(i -> Optional.ofNullable(stored[0]));
            when(repo.save(any(ModuleEnablement.class))).thenAnswer(i -> {
                stored[0] = i.getArgument(0);
                return stored[0];
            });
            when(provisioning.provision(ModuleType.CACHE, projectId, ENV, "admin")).thenReturn("ref");

            ModuleEnablement r = service.enable(orgId, projectId, ENV, ModuleType.CACHE, "admin");

            assertThat(r.getStatus()).isEqualTo(ModuleStatus.ACTIVE);
            assertThat(r.getLastError()).isNull();
            assertThat(r.getRequestedBy()).isEqualTo("admin");
            verify(guard).requireManager(orgId, "admin");
            verify(publisher).moduleEnableRequested(projectId, ENV, ModuleType.CACHE, "admin");
        }

        @Test
        void provisioningFailureEndsFailedWithError() {
            ModuleEnablement[] stored = new ModuleEnablement[1];
            when(repo.findByProjectIdAndEnvironmentAndModule(any(), any(), any())).thenAnswer(i -> Optional.ofNullable(stored[0]));
            when(repo.save(any(ModuleEnablement.class))).thenAnswer(i -> {
                stored[0] = i.getArgument(0);
                return stored[0];
            });
            when(provisioning.provision(any(), any(), any(), any())).thenThrow(new IllegalStateException("grpc down"));

            ModuleEnablement r = service.enable(orgId, projectId, ENV, ModuleType.CACHE, "admin");

            assertThat(r.getStatus()).isEqualTo(ModuleStatus.FAILED);
            assertThat(r.getLastError()).isEqualTo("grpc down");
        }

        @Test
        void retryAfterFailureReusesRowAndClearsError() {
            ModuleEnablement failed = existing(ModuleStatus.FAILED);
            failed.setLastError("old");
            when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE)).thenReturn(Optional.of(failed));

            ModuleEnablement r = service.enable(orgId, projectId, ENV, ModuleType.CACHE, "admin");

            assertThat(r).isSameAs(failed);
            assertThat(r.getStatus()).isEqualTo(ModuleStatus.ACTIVE);
            assertThat(r.getLastError()).isNull();
        }

        @Test
        void enablingAnAlreadyActiveOrInFlightModuleConflicts() {
            for (ModuleStatus s : List.of(ModuleStatus.ACTIVE, ModuleStatus.PENDING, ModuleStatus.PROVISIONING)) {
                when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE)).thenReturn(Optional.of(existing(s)));

                assertApi(() -> service.enable(orgId, projectId, ENV, ModuleType.CACHE, "a"), HttpStatus.CONFLICT);
            }
            verifyNoInteractions(provisioning);
            verifyNoInteractions(publisher);
        }

        @Test
        void disableActiveModuleEndsDisabled() {
            ModuleEnablement active = existing(ModuleStatus.ACTIVE);
            when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE)).thenReturn(Optional.of(active));

            ModuleEnablement r = service.disable(orgId, projectId, ENV, ModuleType.CACHE, "admin");

            assertThat(r.getStatus()).isEqualTo(ModuleStatus.DISABLED);
            verify(provisioning).deprovision(ModuleType.CACHE, projectId, ENV, "admin");
            verify(publisher).moduleDisableRequested(projectId, ENV, ModuleType.CACHE, "admin");
        }

        @Test
        void disableFailureRestoresActiveAndReturns502() {
            ModuleEnablement active = existing(ModuleStatus.ACTIVE);
            when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE)).thenReturn(Optional.of(active));
            doThrow(new IllegalStateException("cache refused")).when(provisioning).deprovision(any(), any(), any(), any());

            assertApi(() -> service.disable(orgId, projectId, ENV, ModuleType.CACHE, "admin"), HttpStatus.BAD_GATEWAY);

            assertThat(active.getStatus()).isEqualTo(ModuleStatus.ACTIVE);
            assertThat(active.getLastError()).isEqualTo("cache refused");
        }

        @Test
        void disableRequiresExistingActiveModule() {
            when(repo.findByProjectIdAndEnvironmentAndModule(projectId, ENV, ModuleType.CACHE))
                    .thenReturn(Optional.empty(), Optional.of(existing(ModuleStatus.FAILED)));

            assertApi(() -> service.disable(orgId, projectId, ENV, ModuleType.CACHE, "a"), HttpStatus.NOT_FOUND);
            assertApi(() -> service.disable(orgId, projectId, ENV, ModuleType.CACHE, "a"), HttpStatus.CONFLICT);
            verifyNoInteractions(provisioning);
        }
    }

    @Nested
    class ProvisioningClient {

        @Mock AuthTenantProvisioningServiceGrpc.AuthTenantProvisioningServiceBlockingStub auth;
        @Mock NotificationTenantProvisioningServiceGrpc.NotificationTenantProvisioningServiceBlockingStub notification;
        @Mock CacheTenantProvisioningServiceGrpc.CacheTenantProvisioningServiceBlockingStub cache;
        @Mock SchedulerTenantProvisioningServiceGrpc.SchedulerTenantProvisioningServiceBlockingStub scheduler;
        @Mock FeatureFlagTenantProvisioningServiceGrpc.FeatureFlagTenantProvisioningServiceBlockingStub flags;

        private ModuleProvisioningClient client() {
            return new ModuleProvisioningClient(auth, notification, cache, scheduler, flags);
        }

        private ProvisionTenantResponse ok(String ref) {
            return ProvisionTenantResponse.newBuilder().setSuccess(true).setResourceReference(ref).build();
        }

        @Test
        void routesEachModuleToItsOwnStub() {
            when(auth.provisionTenant(any())).thenReturn(ok("a"));
            when(notification.provisionTenant(any())).thenReturn(ok("n"));
            when(cache.provisionTenant(any())).thenReturn(ok("c"));
            when(scheduler.provisionTenant(any())).thenReturn(ok("s"));
            when(flags.provisionTenant(any())).thenReturn(ok("f"));
            ModuleProvisioningClient c = client();

            assertThat(c.provision(ModuleType.AUTHENTICATION, projectId, ENV, "u")).isEqualTo("a");
            assertThat(c.provision(ModuleType.NOTIFICATION, projectId, ENV, "u")).isEqualTo("n");
            assertThat(c.provision(ModuleType.CACHE, projectId, ENV, "u")).isEqualTo("c");
            assertThat(c.provision(ModuleType.SCHEDULER, projectId, ENV, "u")).isEqualTo("s");
            assertThat(c.provision(ModuleType.FEATURE_FLAGS, projectId, ENV, "u")).isEqualTo("f");
        }

        @Test
        void requestCarriesProjectEnvironmentAndRequester() {
            when(cache.provisionTenant(any())).thenReturn(ok("c"));

            client().provision(ModuleType.CACHE, projectId, EnvironmentType.PRODUCTION, "me");

            ArgumentCaptor<ProvisionTenantRequest> cap = ArgumentCaptor.forClass(ProvisionTenantRequest.class);
            verify(cache).provisionTenant(cap.capture());
            assertThat(cap.getValue().getProjectId()).isEqualTo(projectId.toString());
            assertThat(cap.getValue().getEnvironment()).isEqualTo("PRODUCTION");
            assertThat(cap.getValue().getRequestedBy()).isEqualTo("me");
        }

        @Test
        void unsuccessfulResponseAndGrpcErrorsBecomeIllegalState() {
            when(cache.provisionTenant(any())).thenReturn(
                    ProvisionTenantResponse.newBuilder().setSuccess(false).setMessage("quota").build());
            when(auth.provisionTenant(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            ModuleProvisioningClient c = client();

            assertThatThrownBy(() -> c.provision(ModuleType.CACHE, projectId, ENV, "u"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("quota");
            assertThatThrownBy(() -> c.provision(ModuleType.AUTHENTICATION, projectId, ENV, "u"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNAVAILABLE");
        }

        @Test
        void deprovisionRoutesAndValidatesResponse() {
            when(scheduler.deprovisionTenant(any())).thenReturn(DeprovisionTenantResponse.newBuilder().setSuccess(true).build());
            when(flags.deprovisionTenant(any())).thenReturn(DeprovisionTenantResponse.newBuilder().setSuccess(false).setMessage("busy").build());
            ModuleProvisioningClient c = client();

            c.deprovision(ModuleType.SCHEDULER, projectId, ENV, "u");
            verify(scheduler).deprovisionTenant(any());
            assertThatThrownBy(() -> c.deprovision(ModuleType.FEATURE_FLAGS, projectId, ENV, "u"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("busy");
        }
    }

    @Nested
    class SdkConfig {

        @Mock ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub apiKeyStub;
        private SdkConfigService service;

        @BeforeEach
        void setUp() {
            service = new SdkConfigService(repo, projectService, guard, apiKeyStub);
            ReflectionTestUtils.setField(service, "gatewayBaseUrl", "https://api.arceus.io");
            Project project = Project.builder().id(projectId).slug("web").build();
            when(projectService.findActiveOrThrow(orgId, projectId)).thenReturn(project);
            lenient().when(repo.findByProjectIdAndEnvironment(projectId, ENV)).thenReturn(List.of(
                    ModuleEnablement.builder().module(ModuleType.CACHE).status(ModuleStatus.ACTIVE).build(),
                    ModuleEnablement.builder().module(ModuleType.AUTHENTICATION).status(ModuleStatus.FAILED).build()));
        }

        @Test
        void newKeyIsReturnedInFull() {
            when(apiKeyStub.getOrCreateProjectPublicKey(any())).thenReturn(
                    GetOrCreateProjectPublicKeyResponse.newBuilder().setCreated(true).setRawKey("pk_new").setKeyPrefix("pk_new00").build());

            SdkConfigResponse r = service.generate(orgId, projectId, ENV, "admin@x.io");

            assertThat(r.apiKey()).isEqualTo("pk_new");
            assertThat(r.gatewayUrl()).isEqualTo("https://api.arceus.io");
            assertThat(r.projectSlug()).isEqualTo("web");
            assertThat(r.environment()).isEqualTo("STAGING");
            assertThat(r.modules()).extracting("module", "enabled", "baseRoute")
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("CACHE", true, "/api/cache"),
                            org.assertj.core.groups.Tuple.tuple("AUTHENTICATION", false, "/api/auth"));
            verify(guard).requireManager(orgId, "admin@x.io");
        }

        @Test
        void existingKeyIsNotRevealedOnlyPlaceholderWithPrefix() {
            when(apiKeyStub.getOrCreateProjectPublicKey(any())).thenReturn(
                    GetOrCreateProjectPublicKeyResponse.newBuilder().setCreated(false).setKeyPrefix("pk_live0").build());

            SdkConfigResponse r = service.generate(orgId, projectId, ENV, "a@b.c");

            assertThat(r.apiKey()).startsWith("<YOUR_EXISTING_pk_live0").doesNotStartWith("pk_");
        }

        @Test
        void authServiceRefusalIs400() {
            when(apiKeyStub.getOrCreateProjectPublicKey(any())).thenReturn(
                    GetOrCreateProjectPublicKeyResponse.newBuilder().setError("Requires OWNER or ADMIN").build());

            assertApi(() -> service.generate(orgId, projectId, ENV, "a@b.c"), HttpStatus.BAD_REQUEST);
        }

        @Test
        void authServiceOutageIs503() {
            when(apiKeyStub.getOrCreateProjectPublicKey(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertApi(() -> service.generate(orgId, projectId, ENV, "a@b.c"), HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        void requestForwardsContext() {
            when(apiKeyStub.getOrCreateProjectPublicKey(any())).thenReturn(GetOrCreateProjectPublicKeyResponse.getDefaultInstance());

            service.generate(orgId, projectId, ENV, "a@b.c");

            ArgumentCaptor<GetOrCreateProjectPublicKeyRequest> cap = ArgumentCaptor.forClass(GetOrCreateProjectPublicKeyRequest.class);
            verify(apiKeyStub).getOrCreateProjectPublicKey(cap.capture());
            assertThat(cap.getValue().getOrganizationId()).isEqualTo(orgId.toString());
            assertThat(cap.getValue().getRequestedBy()).isEqualTo("a@b.c");
        }
    }
}
