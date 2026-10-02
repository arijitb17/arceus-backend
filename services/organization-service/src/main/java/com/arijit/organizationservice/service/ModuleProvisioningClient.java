package com.arijit.organizationservice.service;

import com.arijit.arceus.grpc.tenant.AuthTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.CacheTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantRequest;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantResponse;
import com.arijit.arceus.grpc.tenant.FeatureFlagTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.NotificationTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import com.arijit.arceus.grpc.tenant.SchedulerTenantProvisioningServiceGrpc;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleType;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Calls the one service each {@link ModuleType} maps to - see that enum's
 * javadoc - to actually provision or deprovision a tenant-scoped resource
 * for a project.
 * <p>
 * This is what used to be deployment-service's job: it consumed
 * organization-service's {@code module.enable_requested}/
 * {@code module.disable_requested} events and called exactly one of these
 * same five RPCs, then published a result event back for
 * organization-service to consume asynchronously (see the removed
 * {@code ModuleProvisioningResultConsumer} and
 * {@code module-provisioning-results} queue). With deployment-service gone,
 * calling the target RPC directly and synchronously from
 * {@link ModuleEnablementService} is simpler and just as correct - all five
 * implementations already return promptly (several are honest no-ops
 * pending tenant-scoped work in their own service, per their own javadoc),
 * so there was no real orchestration value left in keeping this async.
 */
@Component
@RequiredArgsConstructor
public class ModuleProvisioningClient {

    private final AuthTenantProvisioningServiceGrpc.AuthTenantProvisioningServiceBlockingStub authStub;
    private final NotificationTenantProvisioningServiceGrpc.NotificationTenantProvisioningServiceBlockingStub notificationStub;
    private final CacheTenantProvisioningServiceGrpc.CacheTenantProvisioningServiceBlockingStub cacheStub;
    private final SchedulerTenantProvisioningServiceGrpc.SchedulerTenantProvisioningServiceBlockingStub schedulerStub;
    private final FeatureFlagTenantProvisioningServiceGrpc.FeatureFlagTenantProvisioningServiceBlockingStub featureFlagStub;

    /**
     * @return the target service's resource_reference (often empty - see
     *         ProvisionTenantResponse's javadoc in tenant-provisioning.proto).
     * @throws IllegalStateException if the gRPC call fails outright or the
     *         target service reports success=false.
     */
    public String provision(ModuleType module, UUID projectId, EnvironmentType environment, String requestedBy) {
        ProvisionTenantRequest request = ProvisionTenantRequest.newBuilder()
                .setProjectId(projectId.toString())
                .setEnvironment(environment.name())
                .setRequestedBy(requestedBy)
                .build();

        try {
            ProvisionTenantResponse response = switch (module) {
                case AUTHENTICATION -> authStub.provisionTenant(request);
                case NOTIFICATION -> notificationStub.provisionTenant(request);
                case CACHE -> cacheStub.provisionTenant(request);
                case SCHEDULER -> schedulerStub.provisionTenant(request);
                case FEATURE_FLAGS -> featureFlagStub.provisionTenant(request);
            };

            if (!response.getSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return response.getResourceReference();
        } catch (StatusRuntimeException ex) {
            throw new IllegalStateException(
                    "gRPC call to provision " + module + " failed: " + ex.getStatus().getCode(), ex);
        }
    }

    /** @throws IllegalStateException on the same terms as {@link #provision}. */
    public void deprovision(ModuleType module, UUID projectId, EnvironmentType environment, String requestedBy) {
        DeprovisionTenantRequest request = DeprovisionTenantRequest.newBuilder()
                .setProjectId(projectId.toString())
                .setEnvironment(environment.name())
                .setRequestedBy(requestedBy)
                .build();

        try {
            DeprovisionTenantResponse response = switch (module) {
                case AUTHENTICATION -> authStub.deprovisionTenant(request);
                case NOTIFICATION -> notificationStub.deprovisionTenant(request);
                case CACHE -> cacheStub.deprovisionTenant(request);
                case SCHEDULER -> schedulerStub.deprovisionTenant(request);
                case FEATURE_FLAGS -> featureFlagStub.deprovisionTenant(request);
            };

            if (!response.getSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
        } catch (StatusRuntimeException ex) {
            throw new IllegalStateException(
                    "gRPC call to deprovision " + module + " failed: " + ex.getStatus().getCode(), ex);
        }
    }
}
