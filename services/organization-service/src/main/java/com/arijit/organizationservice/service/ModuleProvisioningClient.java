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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleProvisioningClient {
    private final AuthTenantProvisioningServiceGrpc.AuthTenantProvisioningServiceBlockingStub authStub;
    private final NotificationTenantProvisioningServiceGrpc.NotificationTenantProvisioningServiceBlockingStub notificationStub;
    private final CacheTenantProvisioningServiceGrpc.CacheTenantProvisioningServiceBlockingStub cacheStub;
    private final SchedulerTenantProvisioningServiceGrpc.SchedulerTenantProvisioningServiceBlockingStub schedulerStub;
    private final FeatureFlagTenantProvisioningServiceGrpc.FeatureFlagTenantProvisioningServiceBlockingStub featureFlagStub;

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
                log.warn("Provisioning {} reported failure: project={} env={} message={}", module, projectId, environment, response.getMessage());
                throw new IllegalStateException(response.getMessage());
            }
            log.info("Provisioned {}: project={} env={} resource={}", module, projectId, environment, response.getResourceReference());
            return response.getResourceReference();
        } catch (StatusRuntimeException ex) {
            log.error("gRPC provision call failed: module={} project={} env={} status={}", module, projectId, environment, ex.getStatus(), ex);
            throw new IllegalStateException(
                    "gRPC call to provision " + module + " failed: " + ex.getStatus().getCode(), ex);
        }
    }

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
                log.warn("Deprovisioning {} reported failure: project={} env={} message={}", module, projectId, environment, response.getMessage());
                throw new IllegalStateException(response.getMessage());
            }
            log.info("Deprovisioned {}: project={} env={}", module, projectId, environment);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC deprovision call failed: module={} project={} env={} status={}", module, projectId, environment, ex.getStatus(), ex);
            throw new IllegalStateException(
                    "gRPC call to deprovision " + module + " failed: " + ex.getStatus().getCode(), ex);
        }
    }
}
