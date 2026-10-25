package com.arijit.monitoringservice.grpc;

import com.arijit.monitoringservice.config.MonitoringProperties;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
@RequiredArgsConstructor
@Slf4j
public class GrpcAuthInterceptor implements ServerInterceptor {
    static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final MonitoringProperties properties;

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
