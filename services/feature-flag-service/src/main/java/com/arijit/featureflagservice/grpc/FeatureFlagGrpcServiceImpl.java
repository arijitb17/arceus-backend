package com.arijit.featureflagservice.grpc;

import com.arijit.arceus.grpc.featureflag.*;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.EvaluationResult;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.service.FlagService;
import com.arijit.featureflagservice.service.FlagValueCodec;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.function.Supplier;

/**
 * gRPC face of feature-flag-service - every RPC calls straight into
 * {@link FlagService}, the same engine FlagController uses, so REST and
 * gRPC callers get identical validation and behavior. See
 * featureflag.proto for the wire shapes this adapts to/from.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FeatureFlagGrpcServiceImpl extends FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceImplBase {

    private final FlagService flagService;
    private final FlagValueCodec valueCodec;
    private final FlagProtoMapper mapper;

    @Override
    public void createFlag(com.arijit.arceus.grpc.featureflag.CreateFlagRequest request, StreamObserver<FlagActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> {
            CreateFlagRequest dto = new CreateFlagRequest(
                    request.getKey(),
                    request.getName(),
                    blankToNull(request.getDescription()),
                    mapper.parseType(request.getFlagType()),
                    mapper.parseEnvironment(request.getEnvironment()),
                    mapper.parseTargetType(request.getTargetType()),
                    valueCodec.fromJsonString(request.getValue(), mapper.parseType(request.getFlagType())),
                    request.getRolloutPercentage() > 0 ? request.getRolloutPercentage() : null,
                    mapper.parseInstant(request.getActivateAtEpochMillis()),
                    mapper.parseInstant(request.getDeactivateAtEpochMillis()),
                    blankToNull(request.getActor())
            );
            return flagService.create(dto);
        }, "Flag created");
    }

    @Override
    public void updateFlag(com.arijit.arceus.grpc.featureflag.UpdateFlagRequest request, StreamObserver<FlagActionResponse> responseObserver) {
        respondWithAction(responseObserver, () -> {
            Environment environment = mapper.parseEnvironment(request.getEnvironment());
            FeatureFlag existing = flagService.getOrThrow(request.getKey(), environment);
            UpdateFlagRequest dto = new UpdateFlagRequest(
                    blankToNull(request.getName()),
                    blankToNull(request.getDescription()),
                    request.getValue().isBlank() ? null : valueCodec.fromJsonString(request.getValue(), existing.getType()),
                    request.getRolloutPercentage() > 0 ? request.getRolloutPercentage() : null,
                    mapper.parseInstant(request.getActivateAtEpochMillis()),
                    mapper.parseInstant(request.getDeactivateAtEpochMillis()),
                    blankToNull(request.getActor())
            );
            return flagService.update(request.getKey(), environment, dto);
        }, "Flag updated");
    }

    @Override
    public void deleteFlag(DeleteFlagRequest request, StreamObserver<FlagActionResponse> responseObserver) {
        Environment environment = mapper.parseEnvironment(request.getEnvironment());
        try {
            FeatureFlag flag = flagService.getOrThrow(request.getKey(), environment);
            flagService.delete(request.getKey(), environment, blankToNull(request.getActor()));
            responseObserver.onNext(FlagActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Flag deleted").setFlag(mapper.toProto(flag)).build());
        } catch (ApiException e) {
            responseObserver.onNext(FlagActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getFlag(FlagIdentifier request, StreamObserver<GetFlagResponse> responseObserver) {
        try {
            FeatureFlag flag = flagService.getOrThrow(request.getKey(), mapper.parseEnvironment(request.getEnvironment()));
            responseObserver.onNext(GetFlagResponse.newBuilder().setFound(true).setFlag(mapper.toProto(flag)).build());
        } catch (ApiException e) {
            responseObserver.onNext(GetFlagResponse.newBuilder().setFound(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void listFlags(ListFlagsRequest request, StreamObserver<ListFlagsResponse> responseObserver) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Environment environment = request.getEnvironment().isBlank() ? null : mapper.parseEnvironment(request.getEnvironment());
        FlagStatus status = request.getStatus().isBlank() ? null : FlagStatus.valueOf(request.getStatus().toUpperCase());

        var result = flagService.list(environment, status, pageable);
        var responseBuilder = ListFlagsResponse.newBuilder()
                .setTotalElements(result.getTotalElements())
                .setTotalPages(result.getTotalPages())
                .setPage(result.getNumber())
                .setSize(result.getSize());
        result.getContent().forEach(flag -> responseBuilder.addFlags(mapper.toProto(flag)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void evaluateFlag(EvaluateFlagRequest request, StreamObserver<EvaluateFlagResponse> responseObserver) {
        Environment environment = mapper.parseEnvironment(request.getEnvironment());
        Object defaultValue = request.getDefaultValue().isBlank() ? null : parseDefaultValue(request.getDefaultValue());

        EvaluationResult result = flagService.evaluate(request.getKey(), environment, blankToNull(request.getBucketKey()), defaultValue);

        responseObserver.onNext(EvaluateFlagResponse.newBuilder()
                .setFound(result.found())
                .setEnabled(result.enabled())
                .setValue(result.value() == null ? "null" : valueCodec.toJsonString(result.value()))
                .setFlagType(result.type() != null ? result.type().name() : "")
                .setFromCache(result.fromCache())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("feature-flag-service").build());
        responseObserver.onCompleted();
    }

    private void respondWithAction(StreamObserver<FlagActionResponse> responseObserver, Supplier<FeatureFlag> action, String successMessage) {
        try {
            FeatureFlag flag = action.get();
            responseObserver.onNext(FlagActionResponse.newBuilder()
                    .setSuccess(true).setMessage(successMessage).setFlag(mapper.toProto(flag)).build());
        } catch (ApiException e) {
            responseObserver.onNext(FlagActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    private Object parseDefaultValue(String json) {
        return valueCodec.fromJsonString(json, com.arijit.featureflagservice.entity.FlagType.JSON);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
