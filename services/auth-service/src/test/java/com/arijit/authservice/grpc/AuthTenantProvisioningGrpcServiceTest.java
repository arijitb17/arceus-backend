package com.arijit.authservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.tenant.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthTenantProvisioningGrpcServiceTest {

    private final AuthTenantProvisioningGrpcService grpc = new AuthTenantProvisioningGrpcService();
    @Mock StreamObserver<ProvisionTenantResponse> provisionObserver;
    @Mock StreamObserver<DeprovisionTenantResponse> deprovisionObserver;
    @Mock StreamObserver<PingResponse> pingObserver;

    @Test
    void provisionAcknowledgesAsNoOp() {
        grpc.provisionTenant(ProvisionTenantRequest.newBuilder().setProjectId("p").setEnvironment("dev").setRequestedBy("a").build(),
                provisionObserver);

        ArgumentCaptor<ProvisionTenantResponse> cap = ArgumentCaptor.forClass(ProvisionTenantResponse.class);
        verify(provisionObserver).onNext(cap.capture());
        verify(provisionObserver).onCompleted();
        assertThat(cap.getValue().getSuccess()).isTrue();
        assertThat(cap.getValue().getResourceReference()).isEmpty();
    }

    @Test
    void deprovisionAcknowledges() {
        grpc.deprovisionTenant(DeprovisionTenantRequest.getDefaultInstance(), deprovisionObserver);

        ArgumentCaptor<DeprovisionTenantResponse> cap = ArgumentCaptor.forClass(DeprovisionTenantResponse.class);
        verify(deprovisionObserver).onNext(cap.capture());
        verify(deprovisionObserver).onCompleted();
        assertThat(cap.getValue().getSuccess()).isTrue();
    }

    @Test
    void ping() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObserver);

        ArgumentCaptor<PingResponse> cap = ArgumentCaptor.forClass(PingResponse.class);
        verify(pingObserver).onNext(cap.capture());
        assertThat(cap.getValue().getService()).isEqualTo("auth-service");
    }
}
