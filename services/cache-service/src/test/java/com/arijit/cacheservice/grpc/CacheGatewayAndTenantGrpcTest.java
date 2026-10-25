package com.arijit.cacheservice.grpc;

import com.arijit.arceus.grpc.gateway.*;
import com.arijit.arceus.grpc.tenant.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class CacheGatewayAndTenantGrpcTest {

    @Mock RestTemplate restTemplate;
    @Mock StreamObserver<HttpResponseMessage> httpObs;
    @Mock StreamObserver<PingResponse> pingObs;
    @Mock StreamObserver<ProvisionTenantResponse> provObs;
    @Mock StreamObserver<DeprovisionTenantResponse> deprovObs;

    private CacheGatewayGrpcService gateway;

    @BeforeEach
    void setUp() {
        gateway = new CacheGatewayGrpcService();
        ReflectionTestUtils.setField(gateway, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(gateway, "localPort", 8085);
    }

    private HttpResponseMessage response() {
        ArgumentCaptor<HttpResponseMessage> cap = ArgumentCaptor.forClass(HttpResponseMessage.class);
        verify(httpObs).onNext(cap.capture());
        verify(httpObs).onCompleted();
        return cap.getValue();
    }

    @Test
    void forwardBuildsLocalUriAndRelaysResponse() {
        HttpHeaders h = new HttpHeaders();
        h.add("Content-Type", "application/json");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>("{}".getBytes(StandardCharsets.UTF_8), h, HttpStatus.OK));

        gateway.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/cache/ns/k").setQueryString("x=1")
                .setRequestId("rid")
                .addHeaders(HttpHeader.newBuilder().setName("X-User-Email").setValue("a@b.c")).build(), httpObs);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.GET), entity.capture(), eq(byte[].class));
        assertThat(uri.getValue().toString()).isEqualTo("http://localhost:8085/cache/ns/k?x=1");
        assertThat(entity.getValue().getHeaders().getFirst("X-User-Email")).isEqualTo("a@b.c");
        assertThat(entity.getValue().getHeaders().getFirst("X-Request-Id")).isEqualTo("rid");
        assertThat(entity.getValue().getBody()).isNull();
        assertThat(response().getStatus()).isEqualTo(200);
    }

    @Test
    void upstreamErrorRelayedAsStatus() {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", new HttpHeaders(),
                        "missing".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        gateway.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/x").build(), httpObs);

        HttpResponseMessage r = response();
        assertThat(r.getStatus()).isEqualTo(404);
        assertThat(r.getBody()).isEqualTo(ByteString.copyFromUtf8("missing"));
    }

    @Test
    void unreachableBecomes500() {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new ResourceAccessException("refused \"x\""));

        gateway.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/x").build(), httpObs);

        HttpResponseMessage r = response();
        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(r.getBody().toStringUtf8()).contains("CACHE_SERVICE_UNREACHABLE").contains("refused 'x'");
    }

    @Test
    void pingAndTenantProvisioning() {
        gateway.ping(PingRequest.getDefaultInstance(), pingObs);
        ArgumentCaptor<PingResponse> ping = ArgumentCaptor.forClass(PingResponse.class);
        verify(pingObs).onNext(ping.capture());
        assertThat(ping.getValue().getService()).isEqualTo("cache-service");

        CacheTenantProvisioningGrpcService tenant = new CacheTenantProvisioningGrpcService();
        tenant.provisionTenant(ProvisionTenantRequest.newBuilder().setProjectId("proj").setEnvironment("prod").build(), provObs);
        ArgumentCaptor<ProvisionTenantResponse> prov = ArgumentCaptor.forClass(ProvisionTenantResponse.class);
        verify(provObs).onNext(prov.capture());
        verify(provObs).onCompleted();
        assertThat(prov.getValue().getSuccess()).isTrue();
        assertThat(prov.getValue().getResourceReference()).isEqualTo("proj:prod");

        tenant.deprovisionTenant(DeprovisionTenantRequest.getDefaultInstance(), deprovObs);
        ArgumentCaptor<DeprovisionTenantResponse> dep = ArgumentCaptor.forClass(DeprovisionTenantResponse.class);
        verify(deprovObs).onNext(dep.capture());
        assertThat(dep.getValue().getSuccess()).isTrue();
    }
}
