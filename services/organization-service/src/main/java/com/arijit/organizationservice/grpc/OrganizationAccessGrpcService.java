package com.arijit.organizationservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.organization.CheckManagerAccessRequest;
import com.arijit.arceus.grpc.organization.CheckManagerAccessResponse;
import com.arijit.arceus.grpc.organization.CheckMemberAccessResponse;
import com.arijit.arceus.grpc.organization.CheckProjectExistsRequest;
import com.arijit.arceus.grpc.organization.CheckProjectExistsResponse;
import com.arijit.arceus.grpc.organization.GetTrackedBranchesRequest;
import com.arijit.arceus.grpc.organization.GetTrackedBranchesResponse;
import com.arijit.arceus.grpc.organization.OrganizationAccessGrpcServiceGrpc;
import com.arijit.arceus.grpc.organization.TrackedBranch;
import com.arijit.organizationservice.entity.OrgRole;
import com.arijit.organizationservice.repository.ProjectEnvironmentRepository;
import com.arijit.organizationservice.repository.ProjectRepository;
import com.arijit.organizationservice.service.OrgAccessGuard;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class OrganizationAccessGrpcService extends OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceImplBase {
    private final OrgAccessGuard accessGuard;
    private final ProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;

    @Override
    public void checkManagerAccess(CheckManagerAccessRequest request, StreamObserver<CheckManagerAccessResponse> responseObserver) {
        Optional<OrgRole> role = parseUuid(request.getOrganizationId())
                .flatMap(orgId -> accessGuard.roleOfIfMember(orgId, request.getUserEmail()));

        boolean authorized = role.filter(r -> r == OrgRole.OWNER || r == OrgRole.ADMIN).isPresent();

        responseObserver.onNext(CheckManagerAccessResponse.newBuilder()
                .setAuthorized(authorized)
                .setRole(role.map(Enum::name).orElse(""))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void checkMemberAccess(CheckManagerAccessRequest request, StreamObserver<CheckMemberAccessResponse> responseObserver) {
        Optional<OrgRole> role = parseUuid(request.getOrganizationId())
                .flatMap(orgId -> accessGuard.roleOfIfMember(orgId, request.getUserEmail()));

        responseObserver.onNext(CheckMemberAccessResponse.newBuilder()
                .setAuthorized(role.isPresent())
                .setRole(role.map(Enum::name).orElse(""))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void checkProjectExists(CheckProjectExistsRequest request, StreamObserver<CheckProjectExistsResponse> responseObserver) {
        boolean exists = false;
        Optional<UUID> organizationId = parseUuid(request.getOrganizationId());
        Optional<UUID> projectId = parseUuid(request.getProjectId());
        if (organizationId.isPresent() && projectId.isPresent()) {
            exists = projectRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(projectId.get(), organizationId.get());
        }

        responseObserver.onNext(CheckProjectExistsResponse.newBuilder().setExists(exists).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTrackedBranches(GetTrackedBranchesRequest request, StreamObserver<GetTrackedBranchesResponse> responseObserver) {
        GetTrackedBranchesResponse.Builder builder = GetTrackedBranchesResponse.newBuilder();

        parseUuid(request.getProjectId()).ifPresent(projectId ->
                environmentRepository.findByProjectId(projectId).forEach(env ->
                        builder.addBranches(TrackedBranch.newBuilder()
                                .setEnvironment(env.getEnvironment().name())
                                .setBranch(env.getTrackedBranch() == null ? "" : env.getTrackedBranch())
                                .build())));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("organization-service").build());
        responseObserver.onCompleted();
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
