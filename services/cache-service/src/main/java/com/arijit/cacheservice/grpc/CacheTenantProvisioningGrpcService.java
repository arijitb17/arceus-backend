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

/**
 * The provisioning half of the module-enablement workflow (architecture
 * design §4), for the CACHE module. Similar to feature-flag-service's
 * implementation: cache-service's existing key/namespace model (see
 * CacheKeyRef) already lets any caller scope its own keys by an arbitrary
 * namespace string, so "provisioning" a project's cache access today is
 * a real no-op, not a placeholder - the project's SDK config (design §9)
 * is what will hand the generated client a project-scoped namespace
 * convention to use, no allocation step required here.
 * <p>
 * A hard per-project quota/eviction-priority (distinct from today's
 * shared quota) is real future work, not yet modeled - see the design
 * doc's §11 row for cache-service.
 */
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

        // No dedicated allocation exists to release (see provisionTenant above). Once a hard
        // per-project quota exists, this is where its keys would be evicted/archived.
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
