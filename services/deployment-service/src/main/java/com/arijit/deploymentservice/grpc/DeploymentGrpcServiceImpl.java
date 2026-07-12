package com.arijit.deploymentservice.grpc;

import com.arijit.arceus.grpc.deployment.*;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.deploymentservice.dto.RollbackRequest;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import com.arijit.deploymentservice.exception.ApiException;
import com.arijit.deploymentservice.service.DeploymentService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

/**
 * gRPC face of deployment-service - every RPC calls straight into
 * {@link DeploymentService}, the same engine DeploymentController uses,
 * so REST and gRPC callers get identical validation and behavior. See
 * deployment.proto for the wire shapes this adapts to/from, and its
 * Javadoc for why approve/cancel are REST-only and not mirrored here.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class DeploymentGrpcServiceImpl extends DeploymentGrpcServiceGrpc.DeploymentGrpcServiceImplBase {

    private final DeploymentService deploymentService;
    private final DeploymentProtoMapper mapper;

    @Override
    public void createDeployment(CreateDeploymentRequest request, StreamObserver<CreateDeploymentResponse> responseObserver) {
        var createRequest = new com.arijit.deploymentservice.dto.CreateDeploymentRequest(
                request.getServiceName(),
                request.getServiceVersion(),
                request.getDockerImage(),
                Environment.valueOf(request.getEnvironment().toUpperCase()),
                request.getCreatedBy(),
                blankToNull(request.getReleaseNotes()),
                blankToNull(request.getCorrelationId()),
                request.getScheduledAtEpochMillis() > 0 ? Instant.ofEpochMilli(request.getScheduledAtEpochMillis()) : null,
                blankToNull(request.getFeatureFlagKey())
        );

        var deployment = deploymentService.create(createRequest);
        responseObserver.onNext(CreateDeploymentResponse.newBuilder().setDeployment(mapper.toProto(deployment)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void deployService(DeploymentIdRequest request, StreamObserver<DeploymentActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> deploymentService.deploy(parseId(request.getId())), "Deployment executed");
    }

    @Override
    public void rollbackDeployment(com.arijit.arceus.grpc.deployment.RollbackDeploymentRequest request,
                                    StreamObserver<DeploymentActionResponse> responseObserver) {
        // Resolve serviceName+environment to the deployment currently live for
        // it, since DeploymentService#rollback needs a reference deployment id
        // (see that method's Javadoc) but gRPC callers reason in terms of
        // serviceName+environment rather than a specific deployment id.
        try {
            Environment environment = Environment.valueOf(request.getEnvironment().toUpperCase());
            var current = deploymentService.list(request.getServiceName(), environment, DeploymentStatus.RUNNING,
                            PageRequest.of(0, 1)).stream().findFirst()
                    .orElseThrow(() -> new ApiException(
                            "No currently running deployment for " + request.getServiceName() + " in " + environment,
                            org.springframework.http.HttpStatus.CONFLICT));

            RollbackRequest rollbackRequest = new RollbackRequest(
                    blankToNull(request.getTargetVersion()), request.getInitiatedBy(), blankToNull(request.getCorrelationId()));

            var deployment = deploymentService.rollback(current.getId(), rollbackRequest);
            responseObserver.onNext(DeploymentActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Rollback deployment created").setDeployment(mapper.toProto(deployment)).build());
        } catch (ApiException e) {
            responseObserver.onNext(DeploymentActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getDeployment(DeploymentIdRequest request, StreamObserver<GetDeploymentResponse> responseObserver) {
        try {
            var deployment = deploymentService.getOrThrow(parseId(request.getId()));
            responseObserver.onNext(GetDeploymentResponse.newBuilder().setFound(true).setDeployment(mapper.toProto(deployment)).build());
        } catch (ApiException e) {
            responseObserver.onNext(GetDeploymentResponse.newBuilder().setFound(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void listDeployments(ListDeploymentsRequest request, StreamObserver<ListDeploymentsResponse> responseObserver) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String serviceName = request.getServiceName().isBlank() ? null : request.getServiceName();
        Environment environment = request.getEnvironment().isBlank() ? null : Environment.valueOf(request.getEnvironment().toUpperCase());
        DeploymentStatus status = request.getStatus().isBlank() ? null : DeploymentStatus.valueOf(request.getStatus().toUpperCase());

        var result = deploymentService.list(serviceName, environment, status, pageable);
        var responseBuilder = ListDeploymentsResponse.newBuilder()
                .setTotalElements(result.getTotalElements())
                .setTotalPages(result.getTotalPages())
                .setPage(result.getNumber())
                .setSize(result.getSize());
        result.getContent().forEach(d -> responseBuilder.addDeployments(mapper.toProto(d)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getDeploymentHistory(GetDeploymentHistoryRequest request, StreamObserver<GetDeploymentHistoryResponse> responseObserver) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String serviceName = request.getServiceName().isBlank() ? null : request.getServiceName();
        Environment environment = request.getEnvironment().isBlank() ? null : Environment.valueOf(request.getEnvironment().toUpperCase());

        var result = deploymentService.history(serviceName, environment, pageable);
        var responseBuilder = GetDeploymentHistoryResponse.newBuilder()
                .setTotalElements(result.getTotalElements())
                .setTotalPages(result.getTotalPages())
                .setPage(result.getNumber())
                .setSize(result.getSize());
        result.getContent().forEach(d -> responseBuilder.addDeployments(mapper.toProto(d)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("deployment-service").build());
        responseObserver.onCompleted();
    }

    private void respondWithAction(StreamObserver<DeploymentActionResponse> responseObserver,
                                    java.util.function.Supplier<com.arijit.deploymentservice.entity.Deployment> action,
                                    String successMessage) {
        try {
            var deployment = action.get();
            responseObserver.onNext(DeploymentActionResponse.newBuilder()
                    .setSuccess(true).setMessage(successMessage).setDeployment(mapper.toProto(deployment)).build());
        } catch (ApiException e) {
            responseObserver.onNext(DeploymentActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException("'" + id + "' is not a valid deployment id", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
