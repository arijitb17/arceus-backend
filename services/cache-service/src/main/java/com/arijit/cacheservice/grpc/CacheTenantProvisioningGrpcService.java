package com.arijit.cacheservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.CacheTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantRequest;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantResponse;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
public class CacheTenantProvisioningGrpcService
        extends CacheTenantProvisioningServiceGrpc.CacheTenantProvisioningServiceImplBase {
    @Override
    public void provisionTenant(ProvisionTenantRequest request, StreamObserver<ProvisionTenantResponse> responseObserver) {
        log.info("Provisioning CACHE for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        String namespace = request.getProjectId() + ":" + request.getEnvironment();
        responseObserver.onNext(ProvisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("No allocation required - cache keys for this project should use namespace \"" + namespace + "\".")
                .setResourceReference(namespace)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void deprovisionTenant(DeprovisionTenantRequest request, StreamObserver<DeprovisionTenantResponse> responseObserver) {
        log.info("Deprovisioning CACHE for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(DeprovisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("No dedicated resources were allocated for this project - nothing to release.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("cache-service").build());
        responseObserver.onCompleted();
    }
}
