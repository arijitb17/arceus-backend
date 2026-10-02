package com.arijit.authservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.AuthTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantRequest;
import com.arijit.arceus.grpc.tenant.DeprovisionTenantResponse;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * The provisioning half of the module-enablement workflow (architecture
 * design §4), for the AUTHENTICATION module. Deliberately an honest
 * acknowledged no-op today, not a fake success dressed up as real work:
 * auth-service's {@code Role}/{@code Permission} entities aren't yet
 * tenant-scoped (see the design doc's §11 and the platform's own
 * README_BACKEND_GAPS.md - closing that RBAC gap is listed as required
 * groundwork, not done here). Once that work lands, provisioning AUTH for
 * a project means creating that project's bootstrap Role/Permission rows
 * here; until then this endpoint exists (and organization-service's
 * ModuleProvisioningClient calls it end-to-end already) so the
 * orchestration pipeline is real and complete, with only this one
 * method's body left to fill in - not the whole call chain.
 */
@GrpcService
@Slf4j
public class AuthTenantProvisioningGrpcService
        extends AuthTenantProvisioningServiceGrpc.AuthTenantProvisioningServiceImplBase {

    @Override
    public void provisionTenant(ProvisionTenantRequest request, StreamObserver<ProvisionTenantResponse> responseObserver) {
        log.info("Provisioning AUTHENTICATION for project={} environment={} requestedBy={} - no-op pending tenant-scoped RBAC (see README_BACKEND_GAPS.md)",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(ProvisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Acknowledged - auth-service does not yet scope Role/Permission by project; " +
                        "this project currently shares the platform's existing RBAC model.")
                .setResourceReference("")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void deprovisionTenant(DeprovisionTenantRequest request, StreamObserver<DeprovisionTenantResponse> responseObserver) {
        log.info("Deprovisioning AUTHENTICATION for project={} environment={} requestedBy={}",
                request.getProjectId(), request.getEnvironment(), request.getRequestedBy());

        responseObserver.onNext(DeprovisionTenantResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Acknowledged - no tenant-scoped resources were allocated for this project.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("auth-service").build());
        responseObserver.onCompleted();
    }
}
