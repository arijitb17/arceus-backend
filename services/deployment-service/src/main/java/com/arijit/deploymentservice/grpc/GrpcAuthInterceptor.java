package com.arijit.deploymentservice.grpc;

import com.arijit.deploymentservice.config.DeploymentProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * gRPC-side equivalent of InternalTokenAuthFilter - every RPC must carry
 * the same {@code x-internal-token} metadata key the REST API expects as
 * {@code X-Internal-Token}, checked against the same
 * {@code arceus.deployment.internal.api-token} configuration. {@code Ping}
 * is deliberately exempt, same as every other service's gRPC auth
 * interceptor, so a caller's own startup liveness probe doesn't need the
 * token yet.
 */
@GrpcGlobalServerInterceptor
@RequiredArgsConstructor
@Slf4j
public class GrpcAuthInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final DeploymentProperties properties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        if (call.getMethodDescriptor().getFullMethodName().endsWith("/Ping")) {
            return next.startCall(call, headers);
        }

        String presented = headers.get(TOKEN_HEADER);
        String expected = properties.internal().apiToken();
        if (presented == null || expected == null || !presented.equals(expected)) {
            log.warn("Rejected unauthenticated gRPC call to {}", call.getMethodDescriptor().getFullMethodName());
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid x-internal-token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return next.startCall(call, headers);
    }
}
