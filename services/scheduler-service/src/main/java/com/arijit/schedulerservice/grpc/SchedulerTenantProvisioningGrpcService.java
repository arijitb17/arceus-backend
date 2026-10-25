package com.arijit.schedulerservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantRequest;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantResponse;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import com.arijit.arceus.grpc.tenant.SchedulerTenantProvisioningServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
public class SchedulerTenantProvisioningGrpcService
        extends SchedulerTenantProvisioningServiceGrpc.SchedulerTenantProvisioningServiceImplBase {
    @Override
    public void provisionTenant(ProvisionTenantRequest request, StreamObserver<ProvisionTenantResponse> responseObserver) {
        log.info("Provisioning SCHEDULER for project={} environment={} requestedBy={} - no-op pending tenant-scoped job namespaces",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(ProvisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Acknowledged - scheduler-service does not yet group jobs by project; " +
                        "this project currently shares the platform's default job namespace.")
                .setResourceReference("")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void deprovisionTenant(DeprovisionTenantRequest request, StreamObserver<DeprovisionTenantResponse> responseObserver) {
        log.info("Deprovisioning SCHEDULER for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(DeprovisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Acknowledged - no tenant-scoped resources were allocated for this project.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("scheduler-service").build());
        responseObserver.onCompleted();
    }
}
