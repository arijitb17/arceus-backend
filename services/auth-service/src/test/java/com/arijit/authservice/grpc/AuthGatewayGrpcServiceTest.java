package com.arijit.authservice.grpc;

import com.arijit.arceus.grpc.gateway.*;
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
class AuthGatewayGrpcServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock StreamObserver<HttpResponseMessage> observer;
    @Mock StreamObserver<PingResponse> pingObserver;

    private AuthGatewayGrpcService grpc;

    @BeforeEach
    void setUp() {
        grpc = new AuthGatewayGrpcService();
        ReflectionTestUtils.setField(grpc, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(grpc, "localPort", 8081);
    }

    private HttpResponseMessage captureResponse() {
        ArgumentCaptor<HttpResponseMessage> cap = ArgumentCaptor.forClass(HttpResponseMessage.class);
        verify(observer).onNext(cap.capture());
        verify(observer).onCompleted();
        return cap.getValue();
    }

    @Test
    void forwardsRequestToLocalPortWithHeadersBodyAndRequestId() {
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.add("Content-Type", "application/json");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), respHeaders, HttpStatus.CREATED));

        grpc.forward(HttpRequestMessage.newBuilder()
                .setMethod("POST").setPath("/api/auth/login").setQueryString("a=1&b=2")
                .setRequestId("req-1")
                .addHeaders(HttpHeader.newBuilder().setName("Authorization").setValue("Bearer t").build())
                .setBody(ByteString.copyFromUtf8("{}"))
                .build(), observer);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.POST), entity.capture(), eq(byte[].class));
        assertThat(uri.getValue().toString()).isEqualTo("http://localhost:8081/api/auth/login?a=1&b=2");
        assertThat(entity.getValue().getHeaders().getFirst("Authorization")).isEqualTo("Bearer t");
        assertThat(entity.getValue().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
        assertThat((byte[]) entity.getValue().getBody()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));

        HttpResponseMessage r = captureResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        assertThat(r.getBody().toStringUtf8()).isEqualTo("{\"ok\":true}");
        assertThat(r.getHeadersList()).anySatisfy(h -> {
            assertThat(h.getName()).isEqualToIgnoringCase("Content-Type");
            assertThat(h.getValue()).isEqualTo("application/json");
        });
    }

    @Test
    void emptyBodyAndQuerySentAsNull() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok().build());

        grpc.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/api/users/me").build(), observer);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.GET), entity.capture(), eq(byte[].class));
        assertThat(uri.getValue().toString()).isEqualTo("http://localhost:8081/api/users/me");
        assertThat(entity.getValue().getBody()).isNull();
        assertThat(entity.getValue().getHeaders().containsKey("X-Request-Id")).isFalse();

        HttpResponseMessage r = captureResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getBody()).isEmpty();
    }

    @Test
    void upstreamErrorStatusIsRelayedNotThrown() {
        HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                new HttpHeaders(), "{\"message\":\"nope\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(ex);

        grpc.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/x").build(), observer);

        HttpResponseMessage r = captureResponse();
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getBody().toStringUtf8()).contains("nope");
    }

    @Test
    void connectivityFailureBecomes500WithSanitisedMessage() {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new ResourceAccessException("connect \"refused\""));

        grpc.forward(HttpRequestMessage.newBuilder().setMethod("GET").setPath("/x").build(), observer);

        HttpResponseMessage r = captureResponse();
        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(r.getBody().toStringUtf8()).contains("AUTH_SERVICE_UNREACHABLE").contains("connect 'refused'");
    }

    @Test
    void ping() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObserver);

        ArgumentCaptor<PingResponse> cap = ArgumentCaptor.forClass(PingResponse.class);
        verify(pingObserver).onNext(cap.capture());
        assertThat(cap.getValue().getOk()).isTrue();
    }
}
