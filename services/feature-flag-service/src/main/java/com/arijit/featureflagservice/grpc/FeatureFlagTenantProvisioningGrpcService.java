package com.arijit.featureflagservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantRequest;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantResponse;
import com.arijit.arceus.grpc.tenant.FeatureFlagTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * The provisioning half of the module-enablement workflow, for the
 * FEATURE_FLAGS module specifically. Feature Flags needs no new resource
 * created to "enable" it for a tenant - flags are already scoped by an
 * arbitrary {@code environment} string plus a caller-chosen {@code key}
 * (see FlagIdentifier in featureflag.proto), so a tenant's "namespace" is
 * just the convention organization-service applies when choosing flag
 * keys for that project (e.g. prefixing with the project id) - nothing
 * needs to be allocated up front the way cache-service's keyspace quota
 * or scheduler-service's job namespace eventually will.
 * <p>
 * Called directly by organization-service's ModuleProvisioningClient when
 * a developer enables/disables this module for a project - see that
 * class's javadoc for the full call path.
 */
@GrpcService
@Slf4j
public class FeatureFlagTenantProvisioningGrpcService
        extends FeatureFlagTenantProvisioningServiceGrpc.FeatureFlagTenantProvisioningServiceImplBase {

    @Override
    public void provisionTenant(ProvisionTenantRequest request, StreamObserver<ProvisionTenantResponse> responseObserver) {
        log.info("Provisioning FEATURE_FLAGS for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(ProvisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Feature Flags requires no dedicated resource allocation - flag keys for this " +
                        "project should be namespaced by the caller (e.g. \"" + request.getProjectId() + ":<key>\").")
                .setResourceReference("")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void deprovisionTenant(DeprovisionTenantRequest request, StreamObserver<DeprovisionTenantResponse> responseObserver) {
        log.info("Deprovisioning FEATURE_FLAGS for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        // Nothing was allocated at provision time (see provisionTenant above), so there is nothing to
        // release here either. If per-project flag namespaces gain their own bookkeeping later, this is
        // where those rows would be archived - see the architecture design's 30-day retention note (§4).
        responseObserver.onNext(DeprovisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("No dedicated resources were allocated for this project - nothing to release.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("feature-flag-service").build());
        responseObserver.onCompleted();
    }
}
