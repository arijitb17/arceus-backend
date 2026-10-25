package com.arijit.notificationservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.notificationservice.entity.NotificationChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelFlagGateServiceTest {

    @Mock FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub stub;
    @InjectMocks ChannelFlagGateService gate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gate, "environment", "PRODUCTION");
    }

    @Test
    void enabledFlag() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(true).build());

        assertThat(gate.isChannelEnabled(NotificationChannel.EMAIL)).isTrue();
    }

    @Test
    void disabledFlag() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(false).build());

        assertThat(gate.isChannelEnabled(NotificationChannel.SMS)).isFalse();
    }

    @Test
    void missingFlagMeansEnabled() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.newBuilder().setFound(false).build());

        assertThat(gate.isChannelEnabled(NotificationChannel.SLACK)).isTrue();
    }

    @Test
    void outageFailsOpen() {
        when(stub.evaluateFlag(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThat(gate.isChannelEnabled(NotificationChannel.EMAIL)).isTrue();
    }

    @Test
    void flagKeyDerivedFromChannelName() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.getDefaultInstance());

        gate.isChannelEnabled(NotificationChannel.WEBHOOK);

        ArgumentCaptor<EvaluateFlagRequest> cap = ArgumentCaptor.forClass(EvaluateFlagRequest.class);
        verify(stub).evaluateFlag(cap.capture());
        assertThat(cap.getValue().getKey()).isEqualTo("notification.channel.webhook.enabled");
        assertThat(cap.getValue().getEnvironment()).isEqualTo("PRODUCTION");
        assertThat(cap.getValue().getDefaultValue()).isEqualTo("true");
    }
}
