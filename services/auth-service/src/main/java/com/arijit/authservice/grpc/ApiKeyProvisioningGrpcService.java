package com.arijit.authservice.grpc;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyRequest;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyResponse;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyRequest;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyResponse;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.service.ApiKeyService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * See apikey-provisioning.proto's javadoc: this is what lets
 * organization-service's SDK config generation (architecture design §9)
 * get a usable PUBLIC key without this service exposing any key material
 * through a REST endpoint another service could call directly - a gRPC
 * contract is a much narrower surface than "call our REST API as if you
 * were a browser".
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ApiKeyProvisioningGrpcService extends ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceImplBase {

    private final ApiKeyService apiKeyService;

    @Override
    public void getOrCreateProjectPublicKey(GetOrCreateProjectPublicKeyRequest request,
                                             StreamObserver<GetOrCreateProjectPublicKeyResponse> responseObserver) {
        try {
            UUID organizationId = UUID.fromString(request.getOrganizationId());
            UUID projectId = UUID.fromString(request.getProjectId());

            var result = apiKeyService.getOrCreateProjectPublicKey(
                    request.getRequestedBy(), organizationId, projectId, request.getEnvironment());

            responseObserver.onNext(GetOrCreateProjectPublicKeyResponse.newBuilder()
                    .setCreated(result.created())
                    .setRawKey(result.rawKey())
                    .setKeyPrefix(result.keyPrefix())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.error("Malformed GetOrCreateProjectPublicKeyRequest: {}", e.getMessage());
            responseObserver.onNext(GetOrCreateProjectPublicKeyResponse.newBuilder()
                    .setCreated(false).setRawKey("").setKeyPrefix("").setError("Malformed request").build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            log.warn("GetOrCreateProjectPublicKey rejected: {}", e.getMessage());
            responseObserver.onNext(GetOrCreateProjectPublicKeyResponse.newBuilder()
                    .setCreated(false).setRawKey("").setKeyPrefix("").setError(e.getMessage()).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void validateApiKey(ValidateApiKeyRequest request, StreamObserver<ValidateApiKeyResponse> responseObserver) {
        Optional<ApiKey> resolved = apiKeyService.validateAndTouch(request.getRawKey());

        if (resolved.isEmpty()) {
            responseObserver.onNext(ValidateApiKeyResponse.newBuilder().setValid(false).build());
            responseObserver.onCompleted();
            return;
        }

        ApiKey apiKey = resolved.get();
        String roles = apiKey.getUser().getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));

        ValidateApiKeyResponse.Builder builder = ValidateApiKeyResponse.newBuilder()
                .setValid(true)
                .setUserEmail(apiKey.getUser().getEmail())
                .setRoles(roles);

        if (apiKey.getProjectId() != null) {
            builder.setOrganizationId(apiKey.getOrganizationId().toString())
                    .setProjectId(apiKey.getProjectId().toString())
                    .setEnvironment(apiKey.getEnvironment())
                    .setKeyType(apiKey.getKeyType().name())
                    .setScopes(apiKey.getScopes() == null ? "" : apiKey.getScopes());
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("auth-service").build());
        responseObserver.onCompleted();
    }
}
