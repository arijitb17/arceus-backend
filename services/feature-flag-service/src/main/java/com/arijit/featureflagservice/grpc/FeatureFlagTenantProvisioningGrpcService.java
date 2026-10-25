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
