package com.arijit.cacheservice.grpc;

import com.arijit.arceus.grpc.cache.*;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.cacheservice.dto.CacheDataType;
import com.arijit.cacheservice.dto.CachePolicy;
import com.arijit.cacheservice.dto.CacheRequest;
import com.arijit.cacheservice.dto.CacheResponse;
import com.arijit.cacheservice.dto.SerializationFormat;
import com.arijit.cacheservice.dto.TtlPolicy;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.service.CacheStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class CacheGrpcServiceImpl extends CacheGrpcServiceGrpc.CacheGrpcServiceImplBase {
    private final CacheStoreService cacheStoreService;
    private final ObjectMapper objectMapper;

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        try {
            CacheRequest cacheRequest = toCacheRequest(request);
            cacheStoreService.set(cacheRequest);
            responseObserver.onNext(PutResponse.newBuilder()
                    .setSuccess(true)
                    .setNamespace(request.getNamespace())
                    .setKey(request.getKey())
                    .build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void get(CacheKeyRef request, StreamObserver<GetResponse> responseObserver) {
        try {
            Optional<CacheResponse> result = cacheStoreService.get(request.getNamespace(), request.getKey());
            GetResponse response = result
                    .map(r -> GetResponse.newBuilder()
                            .setFound(true)
                            .setNamespace(r.namespace())
                            .setKey(r.key())
                            .setValue(valueToWireString(r.value(), r.serialization()))
                            .setType(r.type().name())
                            .setSerialization(r.serialization().name())
                            .setTtlSecondsRemaining(r.ttlSecondsRemaining())
                            .build())
                    .orElseGet(() -> GetResponse.newBuilder().setFound(false).setNamespace(request.getNamespace()).setKey(request.getKey()).setTtlSecondsRemaining(-2).build());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void delete(CacheKeyRef request, StreamObserver<DeleteResponse> responseObserver) {
        try {
            boolean deleted = cacheStoreService.delete(request.getNamespace(), request.getKey());
            responseObserver.onNext(DeleteResponse.newBuilder().setDeleted(deleted).build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void exists(CacheKeyRef request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            boolean exists = cacheStoreService.exists(request.getNamespace(), request.getKey());
            responseObserver.onNext(ExistsResponse.newBuilder().setExists(exists).build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void expire(ExpireRequest request, StreamObserver<ExpireResponse> responseObserver) {
        try {
            boolean applied = cacheStoreService.expire(request.getNamespace(), request.getKey(), request.getTtlSeconds());
            responseObserver.onNext(ExpireResponse.newBuilder().setSuccess(applied).build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void increment(IncrementRequest request, StreamObserver<IncrementResponse> responseObserver) {
        try {
            long delta = request.getDelta() != 0 ? request.getDelta() : 1;
            long value = cacheStoreService.incrementBy(request.getNamespace(), request.getKey(), delta);
            responseObserver.onNext(IncrementResponse.newBuilder().setValue(value).build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void hashSet(HashSetRequest request, StreamObserver<HashSetResponse> responseObserver) {
        try {
            cacheStoreService.hashSetField(request.getNamespace(), request.getKey(), request.getField(),
                    request.getValue(), SerializationFormat.STRING);
            responseObserver.onNext(HashSetResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void hashGet(HashGetRequest request, StreamObserver<HashGetResponse> responseObserver) {
        try {
            Optional<Object> value = cacheStoreService.hashGetField(request.getNamespace(), request.getKey(), request.getField());
            HashGetResponse response = value
                    .map(v -> HashGetResponse.newBuilder().setFound(true).setValue(String.valueOf(v)).build())
                    .orElseGet(() -> HashGetResponse.newBuilder().setFound(false).build());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void bulkGet(BulkGetRequest request, StreamObserver<BulkGetResponse> responseObserver) {
        try {
            List<com.arijit.cacheservice.dto.CacheKeyRef> refs = new ArrayList<>();
            request.getKeysList().forEach(k -> refs.add(new com.arijit.cacheservice.dto.CacheKeyRef(k.getNamespace(), k.getKey())));

            com.arijit.cacheservice.dto.BulkGetResponse result = cacheStoreService.bulkGet(refs);
            BulkGetResponse.Builder builder = BulkGetResponse.newBuilder();
            result.found().forEach(r -> builder.addResults(GetResponse.newBuilder()
                    .setFound(true)
                    .setNamespace(r.namespace())
                    .setKey(r.key())
                    .setValue(valueToWireString(r.value(), r.serialization()))
                    .setType(r.type().name())
                    .setSerialization(r.serialization().name())
                    .setTtlSecondsRemaining(r.ttlSecondsRemaining())
                    .build()));
            result.missing().forEach(k -> builder.addResults(GetResponse.newBuilder()
                    .setFound(false).setNamespace(k.namespace()).setKey(k.key()).setTtlSecondsRemaining(-2).build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void bulkPut(BulkPutRequest request, StreamObserver<BulkPutResponse> responseObserver) {
        try {
            List<CacheRequest> requests = new ArrayList<>();
            request.getEntriesList().forEach(entry -> requests.add(toCacheRequest(entry)));

            com.arijit.cacheservice.dto.BulkPutResponse result = cacheStoreService.bulkPut(requests);
            BulkPutResponse.Builder builder = BulkPutResponse.newBuilder();
            result.results().forEach(r -> builder.addResults(PutResponse.newBuilder()
                    .setSuccess(r.success())
                    .setNamespace(r.namespace())
                    .setKey(r.key())
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (ApiException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("cache-service").build());
        responseObserver.onCompleted();
    }

    private CacheRequest toCacheRequest(PutRequest request) {
        SerializationFormat serialization = request.getSerialization().isBlank()
                ? SerializationFormat.JSON
                : SerializationFormat.valueOf(request.getSerialization());
        CacheDataType type = request.getType().isBlank() ? CacheDataType.STRING : CacheDataType.valueOf(request.getType());
        TtlPolicy ttlPolicy = request.getTtlPolicy().isBlank() ? TtlPolicy.NO_EXPIRY : TtlPolicy.valueOf(request.getTtlPolicy());
        CachePolicy cachePolicy = request.getCachePolicy().isBlank() ? CachePolicy.CACHE_ASIDE : CachePolicy.valueOf(request.getCachePolicy());

        Instant absoluteExpireAt = request.getAbsoluteExpireAtEpochSeconds() > 0
                ? Instant.ofEpochSecond(request.getAbsoluteExpireAtEpochSeconds())
                : null;
        Long ttlSeconds = request.getTtlSeconds() > 0 ? request.getTtlSeconds() : null;

        Object value = decodeWireValue(request.getValue(), serialization);

        return new CacheRequest(request.getNamespace(), request.getKey(), value, type, serialization,
                request.getCompress(), ttlPolicy, ttlSeconds, absoluteExpireAt, cachePolicy);
    }

    private Object decodeWireValue(String wireValue, SerializationFormat serialization) {
        if (serialization != SerializationFormat.JSON) {
            return wireValue;
        }
        try {
            return objectMapper.readValue(wireValue, Object.class);
        } catch (Exception e) {
            throw new ApiException("value is not valid JSON: " + e.getMessage(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String valueToWireString(Object value, SerializationFormat serialization) {
        if (serialization != SerializationFormat.JSON) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private <T> void fail(StreamObserver<T> responseObserver, ApiException e) {
        Status status = e.getStatus().is4xxClientError() ? Status.INVALID_ARGUMENT : Status.INTERNAL;
        if (status == Status.INTERNAL) {
            log.error("Cache gRPC call failed: {}", e.getMessage(), e);
        } else {
            log.debug("Cache gRPC call rejected: {}", e.getMessage());
        }
        responseObserver.onError(status.withDescription(e.getMessage()).asRuntimeException());
    }
}
