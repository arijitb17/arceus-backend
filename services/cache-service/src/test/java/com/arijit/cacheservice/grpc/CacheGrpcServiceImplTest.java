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
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CacheGrpcServiceImplTest {

    @Mock CacheStoreService store;
    @Mock StreamObserver<PutResponse> putObs;
    @Mock StreamObserver<GetResponse> getObs;
    @Mock StreamObserver<DeleteResponse> delObs;
    @Mock StreamObserver<ExistsResponse> existsObs;
    @Mock StreamObserver<ExpireResponse> expireObs;
    @Mock StreamObserver<IncrementResponse> incObs;
    @Mock StreamObserver<HashSetResponse> hsetObs;
    @Mock StreamObserver<HashGetResponse> hgetObs;
    @Mock StreamObserver<BulkGetResponse> bgetObs;
    @Mock StreamObserver<BulkPutResponse> bputObs;
    @Mock StreamObserver<PingResponse> pingObs;

    private CacheGrpcServiceImpl grpc;

    @BeforeEach
    void setUp() {
        grpc = new CacheGrpcServiceImpl(store, new ObjectMapper());
    }

    private CacheResponse cached(Object value, SerializationFormat ser) {
        return new CacheResponse("ns", "k", value, CacheDataType.STRING, ser, TtlPolicy.NO_EXPIRY, 30,
                Instant.now(), Instant.now());
    }

    private <T> T captured(StreamObserver<T> obs, Class<T> type) {
        ArgumentCaptor<T> cap = ArgumentCaptor.forClass(type);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        return cap.getValue();
    }

    private Status.Code errorCode(StreamObserver<?> obs) {
        ArgumentCaptor<Throwable> cap = ArgumentCaptor.forClass(Throwable.class);
        verify(obs).onError(cap.capture());
        verify(obs, never()).onCompleted();
        return ((StatusRuntimeException) cap.getValue()).getStatus().getCode();
    }

    @Test
    void putDefaultsAndParsesJsonValue() {
        grpc.put(PutRequest.newBuilder().setNamespace("ns").setKey("k").setValue("{\"a\":1}").build(), putObs);

        ArgumentCaptor<CacheRequest> cap = ArgumentCaptor.forClass(CacheRequest.class);
        verify(store).set(cap.capture());
        CacheRequest r = cap.getValue();
        assertThat(r.serialization()).isEqualTo(SerializationFormat.JSON);
        assertThat(r.type()).isEqualTo(CacheDataType.STRING);
        assertThat(r.ttlPolicy()).isEqualTo(TtlPolicy.NO_EXPIRY);
        assertThat(r.cachePolicy()).isEqualTo(CachePolicy.CACHE_ASIDE);
        assertThat(r.value()).isEqualTo(Map.of("a", 1));
        assertThat(r.ttlSeconds()).isNull();
        assertThat(r.absoluteExpireAt()).isNull();
        PutResponse resp = captured(putObs, PutResponse.class);
        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getKey()).isEqualTo("k");
    }

    @Test
    void putMapsExplicitFields() {
        grpc.put(PutRequest.newBuilder().setNamespace("ns").setKey("k").setValue("raw text")
                .setSerialization("STRING").setType("STRING").setCompress(true).setTtlPolicy("CUSTOM")
                .setTtlSeconds(90).setAbsoluteExpireAtEpochSeconds(2_000_000_000L).setCachePolicy("WRITE_THROUGH").build(), putObs);

        ArgumentCaptor<CacheRequest> cap = ArgumentCaptor.forClass(CacheRequest.class);
        verify(store).set(cap.capture());
        CacheRequest r = cap.getValue();
        assertThat(r.value()).isEqualTo("raw text");
        assertThat(r.compress()).isTrue();
        assertThat(r.ttlSeconds()).isEqualTo(90L);
        assertThat(r.absoluteExpireAt()).isEqualTo(Instant.ofEpochSecond(2_000_000_000L));
        assertThat(r.cachePolicy()).isEqualTo(CachePolicy.WRITE_THROUGH);
    }

    @Test
    void putInvalidJsonIsInvalidArgument() {
        grpc.put(PutRequest.newBuilder().setNamespace("ns").setKey("k").setValue("{broken").build(), putObs);

        assertThat(errorCode(putObs)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(store);
    }

    @Test
    void serverSideApiErrorsBecomeInternal() {
        when(store.set(any())).thenThrow(new ApiException("redis down", HttpStatus.INTERNAL_SERVER_ERROR));

        grpc.put(PutRequest.newBuilder().setNamespace("ns").setKey("k").setValue("1").build(), putObs);

        assertThat(errorCode(putObs)).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void getFoundSerialisesJsonValue() {
        when(store.get("ns", "k")).thenReturn(Optional.of(cached(Map.of("a", 1), SerializationFormat.JSON)));

        grpc.get(CacheKeyRef.newBuilder().setNamespace("ns").setKey("k").build(), getObs);

        GetResponse r = captured(getObs, GetResponse.class);
        assertThat(r.getFound()).isTrue();
        assertThat(r.getValue()).isEqualTo("{\"a\":1}");
        assertThat(r.getType()).isEqualTo("STRING");
        assertThat(r.getSerialization()).isEqualTo("JSON");
        assertThat(r.getTtlSecondsRemaining()).isEqualTo(30);
    }

    @Test
    void getNonJsonValueSentAsPlainString() {
        when(store.get("ns", "k")).thenReturn(Optional.of(cached("plain", SerializationFormat.STRING)));

        grpc.get(CacheKeyRef.newBuilder().setNamespace("ns").setKey("k").build(), getObs);

        assertThat(captured(getObs, GetResponse.class).getValue()).isEqualTo("plain");
    }

    @Test
    void getMissReturnsNotFoundWithMinusTwoTtl() {
        when(store.get("ns", "k")).thenReturn(Optional.empty());

        grpc.get(CacheKeyRef.newBuilder().setNamespace("ns").setKey("k").build(), getObs);

        GetResponse r = captured(getObs, GetResponse.class);
        assertThat(r.getFound()).isFalse();
        assertThat(r.getTtlSecondsRemaining()).isEqualTo(-2);
        assertThat(r.getKey()).isEqualTo("k");
    }

    @Test
    void getRejectedNamespaceIsInvalidArgument() {
        when(store.get(any(), any())).thenThrow(new ApiException("ns not allowed", HttpStatus.FORBIDDEN));

        grpc.get(CacheKeyRef.getDefaultInstance(), getObs);

        assertThat(errorCode(getObs)).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void deleteExistsExpire() {
        when(store.delete("ns", "k")).thenReturn(true);
        when(store.exists("ns", "k")).thenReturn(true);
        when(store.expire("ns", "k", 15)).thenReturn(false);
        CacheKeyRef ref = CacheKeyRef.newBuilder().setNamespace("ns").setKey("k").build();

        grpc.delete(ref, delObs);
        grpc.exists(ref, existsObs);
        grpc.expire(ExpireRequest.newBuilder().setNamespace("ns").setKey("k").setTtlSeconds(15).build(), expireObs);

        assertThat(captured(delObs, DeleteResponse.class).getDeleted()).isTrue();
        assertThat(captured(existsObs, ExistsResponse.class).getExists()).isTrue();
        assertThat(captured(expireObs, ExpireResponse.class).getSuccess()).isFalse();
    }

    @Test
    void incrementDefaultsDeltaToOneWhenZero() {
        when(store.incrementBy("ns", "c", 1)).thenReturn(1L);
        when(store.incrementBy("ns", "c", -4)).thenReturn(6L);

        grpc.increment(IncrementRequest.newBuilder().setNamespace("ns").setKey("c").build(), incObs);
        assertThat(captured(incObs, IncrementResponse.class).getValue()).isEqualTo(1);

        StreamObserver<IncrementResponse> second = mock(StreamObserver.class);
        grpc.increment(IncrementRequest.newBuilder().setNamespace("ns").setKey("c").setDelta(-4).build(), second);
        assertThat(captured(second, IncrementResponse.class).getValue()).isEqualTo(6);
    }

    @Test
    void incrementOnWrongTypeIsInvalidArgument() {
        when(store.incrementBy(any(), any(), anyLong())).thenThrow(new ApiException("is LIST", HttpStatus.CONFLICT));

        grpc.increment(IncrementRequest.newBuilder().setNamespace("ns").setKey("c").build(), incObs);

        assertThat(errorCode(incObs)).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void hashSetUsesStringSerialization() {
        grpc.hashSet(HashSetRequest.newBuilder().setNamespace("ns").setKey("h").setField("f").setValue("v").build(), hsetObs);

        verify(store).hashSetField("ns", "h", "f", "v", SerializationFormat.STRING);
        assertThat(captured(hsetObs, HashSetResponse.class).getSuccess()).isTrue();
    }

    @Test
    void hashGetFoundAndNotFound() {
        when(store.hashGetField("ns", "h", "f")).thenReturn(Optional.of(42));
        when(store.hashGetField("ns", "h", "x")).thenReturn(Optional.empty());

        grpc.hashGet(HashGetRequest.newBuilder().setNamespace("ns").setKey("h").setField("f").build(), hgetObs);
        assertThat(captured(hgetObs, HashGetResponse.class).getValue()).isEqualTo("42");

        StreamObserver<HashGetResponse> second = mock(StreamObserver.class);
        grpc.hashGet(HashGetRequest.newBuilder().setNamespace("ns").setKey("h").setField("x").build(), second);
        assertThat(captured(second, HashGetResponse.class).getFound()).isFalse();
    }

    @Test
    void bulkGetMergesFoundThenMissing() {
        when(store.bulkGet(any())).thenReturn(new com.arijit.cacheservice.dto.BulkGetResponse(
                List.of(cached("v", SerializationFormat.STRING)),
                List.of(new com.arijit.cacheservice.dto.CacheKeyRef("ns", "gone"))));

        grpc.bulkGet(BulkGetRequest.newBuilder()
                .addKeys(CacheKeyRef.newBuilder().setNamespace("ns").setKey("k"))
                .addKeys(CacheKeyRef.newBuilder().setNamespace("ns").setKey("gone")).build(), bgetObs);

        BulkGetResponse r = captured(bgetObs, BulkGetResponse.class);
        assertThat(r.getResultsList()).extracting(GetResponse::getFound).containsExactly(true, false);
        assertThat(r.getResults(1).getKey()).isEqualTo("gone");
        assertThat(r.getResults(1).getTtlSecondsRemaining()).isEqualTo(-2);
    }

    @Test
    void bulkPutReportsPerEntrySuccess() {
        when(store.bulkPut(any())).thenReturn(new com.arijit.cacheservice.dto.BulkPutResponse(List.of(
                new com.arijit.cacheservice.dto.BulkPutResponse.BulkPutResult("ns", "a", true, null),
                new com.arijit.cacheservice.dto.BulkPutResponse.BulkPutResult("ns", "b", false, "bad"))));

        grpc.bulkPut(BulkPutRequest.newBuilder()
                .addEntries(PutRequest.newBuilder().setNamespace("ns").setKey("a").setValue("1"))
                .addEntries(PutRequest.newBuilder().setNamespace("ns").setKey("b").setValue("2")).build(), bputObs);

        BulkPutResponse r = captured(bputObs, BulkPutResponse.class);
        assertThat(r.getResultsList()).extracting(PutResponse::getSuccess).containsExactly(true, false);
    }

    @Test
    void pingIdentifiesService() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObs);

        assertThat(captured(pingObs, PingResponse.class).getService()).isEqualTo("cache-service");
    }
}
