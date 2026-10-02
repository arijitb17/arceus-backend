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

/**
 * The provisioning half of the module-enablement workflow (architecture
 * design §4), for the SCHEDULER module. See AuthTenantProvisioningGrpcService's
 * javadoc for why this is an honest acknowledged no-op rather than a fake
 * success: scheduler-service's jobs aren't yet grouped by project. Once
 * they are, this is where a project's own job namespace + quota gets
 * created.
 * <p>
 * Note this class lives under the same gRPC server as scheduler-service's
 * existing {@code GrpcAuthInterceptor}, which requires {@code
 * x-internal-token} on every RPC except Ping - that interceptor is global
 * to the whole gRPC server, so ProvisionTenant/DeprovisionTenant inherit
 * that requirement automatically with no extra wiring needed here.
 */
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
