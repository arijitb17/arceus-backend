package com.arijit.authservice.featureflag;

import com.arijit.authservice.exception.ApiException;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagGateServiceTest {

    @Mock FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub stub;
    private FeatureFlagGateService gate;

    @BeforeEach
    void setUp() {
        gate = new FeatureFlagGateService(stub);
        ReflectionTestUtils.setField(gate, "environment", "STAGING");
    }

    @Test
    void allowsWhenFlagEnabled() {
        when(stub.evaluateFlag(any())).thenReturn(
                EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(true).build());

        assertThatCode(() -> gate.requireEnabled("k", "Thing")).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenFlagNotFound() {
        when(stub.evaluateFlag(any())).thenReturn(
                EvaluateFlagResponse.newBuilder().setFound(false).setEnabled(false).build());

        assertThatCode(() -> gate.requireEnabled("k", "Thing")).doesNotThrowAnyException();
    }

    @Test
    void blocksWithServiceUnavailableWhenDisabled() {
        when(stub.evaluateFlag(any())).thenReturn(
                EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(false).build());

        assertThatThrownBy(() -> gate.requireEnabled("auth.registration.enabled", "Registration"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).isEqualTo("Registration is temporarily disabled");
                });
    }

    @Test
    void failsOpenWhenFeatureFlagServiceUnreachable() {
        when(stub.evaluateFlag(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatCode(() -> gate.requireEnabled("k", "Thing")).doesNotThrowAnyException();
    }

    @Test
    void sendsKeyEnvironmentAndTrueDefault() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.getDefaultInstance());

        gate.requireEnabled("my.flag", "Thing");

        ArgumentCaptor<EvaluateFlagRequest> cap = ArgumentCaptor.forClass(EvaluateFlagRequest.class);
        verify(stub).evaluateFlag(cap.capture());
        assertThat(cap.getValue().getKey()).isEqualTo("my.flag");
        assertThat(cap.getValue().getEnvironment()).isEqualTo("STAGING");
        assertThat(cap.getValue().getDefaultValue()).isEqualTo("true");
    }
}
