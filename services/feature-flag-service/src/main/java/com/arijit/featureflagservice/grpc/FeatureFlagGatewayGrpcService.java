package com.arijit.featureflagservice.grpc;

import com.arijit.arceus.grpc.gateway.FeatureFlagGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.HttpHeader;
import com.arijit.arceus.grpc.gateway.HttpRequestMessage;
import com.arijit.arceus.grpc.gateway.HttpResponseMessage;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * gRPC face of feature-flag-service for the Arceus Gateway. See
 * AuthGatewayGrpcService in auth-service for the rationale: this forwards
 * to FlagController over a loopback HTTP call instead of duplicating its
 * request/response shapes as dedicated RPCs. Does NOT forward to
 * FlagScheduleCallbackController's /internal/** endpoints - those are only
 * ever called by scheduler-service directly, never through the Gateway.
 */
@GrpcService
public class FeatureFlagGatewayGrpcService extends FeatureFlagGatewayServiceGrpc.FeatureFlagGatewayServiceImplBase {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${server.port}")
    private int localPort;

    @Override
    public void forward(HttpRequestMessage request, StreamObserver<HttpResponseMessage> responseObserver) {
        try {
            responseObserver.onNext(doForward(request));
            responseObserver.onCompleted();
        } catch (RestClientResponseException ex) {
            responseObserver.onNext(HttpResponseMessage.newBuilder()
                    .setStatus(ex.getStatusCode().value())
                    .addAllHeaders(toProtoHeaders(ex.getResponseHeaders()))
                    .setBody(ByteString.copyFrom(ex.getResponseBodyAsByteArray()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onNext(HttpResponseMessage.newBuilder()
                    .setStatus(500)
                    .setBody(ByteString.copyFromUtf8(
                            "{\"error\":\"FEATURE_FLAG_SERVICE_UNREACHABLE\",\"message\":\"" + safe(ex.getMessage()) + "\"}"))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("feature-flag-service").build());
        responseObserver.onCompleted();
    }

    private HttpResponseMessage doForward(HttpRequestMessage request) {
        URI uri = UriComponentsBuilder
                .fromUriString("http://localhost:" + localPort + request.getPath())
                .query(request.getQueryString().isBlank() ? null : request.getQueryString())
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        for (HttpHeader h : request.getHeadersList()) {
            headers.add(h.getName(), h.getValue());
        }
        if (!request.getRequestId().isBlank()) {
            headers.add("X-Request-Id", request.getRequestId());
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(
                request.getBody().isEmpty() ? null : request.getBody().toByteArray(), headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);

        return HttpResponseMessage.newBuilder()
                .setStatus(response.getStatusCode().value())
                .addAllHeaders(toProtoHeaders(response.getHeaders()))
                .setBody(ByteString.copyFrom(response.getBody() == null ? new byte[0] : response.getBody()))
                .build();
    }

    private Iterable<HttpHeader> toProtoHeaders(HttpHeaders headers) {
        if (headers == null) return java.util.List.of();
        java.util.List<HttpHeader> out = new java.util.ArrayList<>();
        headers.forEach((name, values) -> values.forEach(value ->
                out.add(HttpHeader.newBuilder().setName(name).setValue(value).build())));
        return out;
    }

    private String safe(String message) {
        return message == null ? "unknown error" : message.replace("\"", "'");
    }
}
