package com.arijit.featureflagservice.grpc;

import com.arijit.arceus.grpc.featureflag.*;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import com.arijit.featureflagservice.FlagTestData;
import com.arijit.featureflagservice.dto.EvaluationResult;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.service.FlagService;
import com.arijit.featureflagservice.service.FlagValueCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FlagGrpcTest {

    @Mock FlagService flagService;
    @Mock StreamObserver<FlagActionResponse> actionObs;
    @Mock StreamObserver<GetFlagResponse> getObs;
    @Mock StreamObserver<ListFlagsResponse> listObs;
    @Mock StreamObserver<EvaluateFlagResponse> evalObs;
    @Mock StreamObserver<PingResponse> pingObs;

    private final FlagValueCodec codec = new FlagValueCodec(new ObjectMapper());
    private FeatureFlagGrpcServiceImpl grpc;
    private final FeatureFlag flag = FlagTestData.booleanFlag("checkout", Environment.STAGING, FlagStatus.ENABLED, true);

    @BeforeEach
    void setUp() {
        grpc = new FeatureFlagGrpcServiceImpl(flagService, codec, new FlagProtoMapper(codec));
    }

    private <T> T captured(StreamObserver<T> obs, Class<T> type) {
        ArgumentCaptor<T> cap = ArgumentCaptor.forClass(type);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        return cap.getValue();
    }

    @Nested
    class Mapper {

        private final FlagProtoMapper mapper = new FlagProtoMapper(codec);

        @Test
        void toProtoCopiesAllFields() {
            flag.setDescription(null);
            flag.setActivateAt(Instant.ofEpochMilli(1_000));

            Flag p = mapper.toProto(flag);

            assertThat(p.getKey()).isEqualTo("checkout");
            assertThat(p.getFlagType()).isEqualTo("BOOLEAN");
            assertThat(p.getEnvironment()).isEqualTo("STAGING");
            assertThat(p.getValue()).isEqualTo("true");
            assertThat(p.getStatus()).isEqualTo("ENABLED");
            assertThat(p.getDescription()).isEmpty();
            assertThat(p.getActivateAtEpochMillis()).isEqualTo(1_000);
            assertThat(p.getDeactivateAtEpochMillis()).isZero();
            assertThat(p.getVersion()).isEqualTo(3);
        }

        @Test
        void parsers() {
            assertThat(mapper.parseType("boolean")).isEqualTo(FlagType.BOOLEAN);
            assertThat(mapper.parseEnvironment(null)).isEqualTo(Environment.GLOBAL);
            assertThat(mapper.parseEnvironment(" ")).isEqualTo(Environment.GLOBAL);
            assertThat(mapper.parseEnvironment("production")).isEqualTo(Environment.PRODUCTION);
            assertThat(mapper.parseTargetType("")).isEqualTo(com.arijit.featureflagservice.entity.TargetType.ENVIRONMENT);
            assertThat(mapper.parseInstant(0)).isNull();
            assertThat(mapper.parseInstant(-5)).isNull();
            assertThat(mapper.parseInstant(5)).isEqualTo(Instant.ofEpochMilli(5));
        }
    }

    @Test
    void createFlagParsesRequestAndRespondsSuccess() {
        when(flagService.create(any())).thenReturn(flag);

        grpc.createFlag(CreateFlagRequest.newBuilder().setKey("checkout").setName("Checkout").setFlagType("boolean")
                .setEnvironment("staging").setValue("true").setRolloutPercentage(30).setActor("alice").build(), actionObs);

        ArgumentCaptor<com.arijit.featureflagservice.dto.CreateFlagRequest> cap =
                ArgumentCaptor.forClass(com.arijit.featureflagservice.dto.CreateFlagRequest.class);
        verify(flagService).create(cap.capture());
        assertThat(cap.getValue().type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(cap.getValue().environment()).isEqualTo(Environment.STAGING);
        assertThat(cap.getValue().value()).isEqualTo(true);
        assertThat(cap.getValue().rolloutPercentage()).isEqualTo(30);
        assertThat(cap.getValue().description()).isNull();
        FlagActionResponse r = captured(actionObs, FlagActionResponse.class);
        assertThat(r.getSuccess()).isTrue();
        assertThat(r.getFlag().getKey()).isEqualTo("checkout");
    }

    @Test
    void createFlagServiceErrorBecomesUnsuccessfulResponse() {
        when(flagService.create(any())).thenThrow(new ApiException("exists", HttpStatus.CONFLICT));

        grpc.createFlag(CreateFlagRequest.newBuilder().setKey("k").setName("n").setFlagType("BOOLEAN").setValue("true").build(), actionObs);

        FlagActionResponse r = captured(actionObs, FlagActionResponse.class);
        assertThat(r.getSuccess()).isFalse();
        assertThat(r.getMessage()).isEqualTo("exists");
    }

    @Test
    void updateFlagUsesExistingTypeToParseValueAndSkipsBlankFields() {
        when(flagService.getOrThrow("checkout", Environment.STAGING)).thenReturn(flag);
        when(flagService.update(eq("checkout"), eq(Environment.STAGING), any())).thenReturn(flag);

        grpc.updateFlag(UpdateFlagRequest.newBuilder().setKey("checkout").setEnvironment("STAGING").setValue("false").build(), actionObs);

        ArgumentCaptor<com.arijit.featureflagservice.dto.UpdateFlagRequest> cap =
                ArgumentCaptor.forClass(com.arijit.featureflagservice.dto.UpdateFlagRequest.class);
        verify(flagService).update(eq("checkout"), eq(Environment.STAGING), cap.capture());
        assertThat(cap.getValue().value()).isEqualTo(false);
        assertThat(cap.getValue().name()).isNull();
        assertThat(cap.getValue().rolloutPercentage()).isNull();
        assertThat(captured(actionObs, FlagActionResponse.class).getSuccess()).isTrue();
    }

    @Test
    void deleteFlagReturnsDeletedSnapshotOrFailure() {
        when(flagService.getOrThrow("checkout", Environment.STAGING)).thenReturn(flag);
        when(flagService.getOrThrow("gone", Environment.GLOBAL)).thenThrow(new ApiException("nf", HttpStatus.NOT_FOUND));

        grpc.deleteFlag(DeleteFlagRequest.newBuilder().setKey("checkout").setEnvironment("STAGING").setActor("x").build(), actionObs);
        FlagActionResponse ok = captured(actionObs, FlagActionResponse.class);
        assertThat(ok.getSuccess()).isTrue();
        verify(flagService).delete("checkout", Environment.STAGING, "x");

        StreamObserver<FlagActionResponse> second = mock(StreamObserver.class);
        grpc.deleteFlag(DeleteFlagRequest.newBuilder().setKey("gone").build(), second);
        assertThat(captured(second, FlagActionResponse.class).getSuccess()).isFalse();
    }

    @Test
    void getFlagFoundAndNotFound() {
        when(flagService.getOrThrow("checkout", Environment.STAGING)).thenReturn(flag);
        when(flagService.getOrThrow("gone", Environment.GLOBAL)).thenThrow(new ApiException("nf", HttpStatus.NOT_FOUND));

        grpc.getFlag(FlagIdentifier.newBuilder().setKey("checkout").setEnvironment("STAGING").build(), getObs);
        assertThat(captured(getObs, GetFlagResponse.class).getFound()).isTrue();

        StreamObserver<GetFlagResponse> second = mock(StreamObserver.class);
        grpc.getFlag(FlagIdentifier.newBuilder().setKey("gone").build(), second);
        assertThat(captured(second, GetFlagResponse.class).getFound()).isFalse();
    }

    @Test
    void enableFlag() {
        when(flagService.enable("checkout", Environment.STAGING, null)).thenReturn(flag);

        grpc.enableFlag(EnableFlagRequest.newBuilder().setKey("checkout").setEnvironment("STAGING").build(), actionObs);

        assertThat(captured(actionObs, FlagActionResponse.class).getMessage()).isEqualTo("Flag enabled");
    }

    @Test
    void listFlagsClampsPagingAndParsesFilters() {
        when(flagService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(flag), PageRequest.of(0, 100), 1));

        grpc.listFlags(ListFlagsRequest.newBuilder().setEnvironment("production").setStatus("enabled").setPage(-4).setSize(5000).build(), listObs);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(flagService).list(eq(Environment.PRODUCTION), eq(FlagStatus.ENABLED), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        ListFlagsResponse r = captured(listObs, ListFlagsResponse.class);
        assertThat(r.getFlagsList()).hasSize(1);
        assertThat(r.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listFlagsDefaultsSizeAndLeavesFiltersNull() {
        when(flagService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        grpc.listFlags(ListFlagsRequest.getDefaultInstance(), listObs);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(flagService).list(isNull(), isNull(), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void evaluateFlagSerialisesValueAndForwardsDefault() {
        when(flagService.evaluate("checkout", Environment.GLOBAL, "user-1", true))
                .thenReturn(new EvaluationResult(true, true, "blue", FlagType.STRING, true));

        grpc.evaluateFlag(EvaluateFlagRequest.newBuilder().setKey("checkout").setBucketKey("user-1").setDefaultValue("true").build(), evalObs);

        EvaluateFlagResponse r = captured(evalObs, EvaluateFlagResponse.class);
        assertThat(r.getFound()).isTrue();
        assertThat(r.getEnabled()).isTrue();
        assertThat(r.getValue()).isEqualTo("\"blue\"");
        assertThat(r.getFlagType()).isEqualTo("STRING");
        assertThat(r.getFromCache()).isTrue();
    }

    @Test
    void evaluateUnknownFlagWithoutDefaultReturnsJsonNull() {
        when(flagService.evaluate("x", Environment.GLOBAL, null, null)).thenReturn(EvaluationResult.notFound(null));

        grpc.evaluateFlag(EvaluateFlagRequest.newBuilder().setKey("x").build(), evalObs);

        EvaluateFlagResponse r = captured(evalObs, EvaluateFlagResponse.class);
        assertThat(r.getFound()).isFalse();
        assertThat(r.getValue()).isEqualTo("null");
        assertThat(r.getFlagType()).isEmpty();
    }

    @Test
    void pingAndTenantProvisioning() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObs);
        assertThat(captured(pingObs, PingResponse.class).getService()).isEqualTo("feature-flag-service");

        StreamObserver<ProvisionTenantResponse> obs = mock(StreamObserver.class);
        new FeatureFlagTenantProvisioningGrpcService().provisionTenant(
                ProvisionTenantRequest.newBuilder().setProjectId("p1").build(), obs);
        ProvisionTenantResponse r = captured(obs, ProvisionTenantResponse.class);
        assertThat(r.getSuccess()).isTrue();
        assertThat(r.getMessage()).contains("p1");
    }

    @Test
    void authInterceptorAllowsPingWithoutToken() {
        var interceptor = new GrpcAuthInterceptor(FlagTestData.properties(true, 60, true));
        ServerCall<Object, Object> call = mock(ServerCall.class);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        when(call.getMethodDescriptor()).thenReturn(descriptor("arceus.featureflag.FeatureFlagGrpcService/Ping"));

        interceptor.interceptCall(call, new Metadata(), next);

        verify(next).startCall(any(), any());
        verify(call, never()).close(any(), any());
    }

    @Test
    void authInterceptorRejectsMissingOrWrongToken() {
        var interceptor = new GrpcAuthInterceptor(FlagTestData.properties(true, 60, true));
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(descriptor("arceus.featureflag.FeatureFlagGrpcService/EvaluateFlag"));

        interceptor.interceptCall(call, new Metadata(), next);
        Metadata wrong = new Metadata();
        wrong.put(GrpcAuthInterceptor.TOKEN_HEADER, "nope");
        interceptor.interceptCall(call, wrong, next);

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call, times(2)).close(status.capture(), any(Metadata.class));
        assertThat(status.getAllValues()).allMatch(s -> s.getCode() == Status.Code.UNAUTHENTICATED);
        verifyNoInteractions(next);
    }

    @Test
    void authInterceptorAcceptsValidToken() {
        var interceptor = new GrpcAuthInterceptor(FlagTestData.properties(true, 60, true));
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(descriptor("arceus.featureflag.FeatureFlagGrpcService/EvaluateFlag"));
        Metadata md = new Metadata();
        md.put(GrpcAuthInterceptor.TOKEN_HEADER, "internal-token");

        interceptor.interceptCall(call, md, next);

        verify(next).startCall(call, md);
        verify(call, never()).close(any(), any());
    }

    private static MethodDescriptor<Object, Object> descriptor(String fullName) {
        MethodDescriptor.Marshaller<Object> m = new MethodDescriptor.Marshaller<>() {
            public java.io.InputStream stream(Object value) { return null; }
            public Object parse(java.io.InputStream stream) { return null; }
        };
        return MethodDescriptor.<Object, Object>newBuilder().setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullName).setRequestMarshaller(m).setResponseMarshaller(m).build();
    }
}
