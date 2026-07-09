package com.arijit.gatewayservice.config;

import com.arijit.arceus.grpc.gateway.AuditGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.AuthGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.NotificationGatewayServiceGrpc;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;

/**
 * Wires up one blocking stub per downstream service, each bound to the
 * named channel configured under spring.grpc.client.channels.* in
 * application.yml. Keeping the channel name ("auth", "notification",
 * "audit", "ratelimit") as the single point of truth for where each
 * service lives means routing code never hardcodes a host/port.
 */
@Configuration
public class GrpcClientConfig {

    @Bean
    public AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub authGatewayStub(GrpcChannelFactory channels) {
        return AuthGatewayServiceGrpc.newBlockingStub(channels.createChannel("auth"));
    }

    @Bean
    public NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notificationGatewayStub(GrpcChannelFactory channels) {
        return NotificationGatewayServiceGrpc.newBlockingStub(channels.createChannel("notification"));
    }

    @Bean
    public AuditGatewayServiceGrpc.AuditGatewayServiceBlockingStub auditGatewayStub(GrpcChannelFactory channels) {
        return AuditGatewayServiceGrpc.newBlockingStub(channels.createChannel("audit"));
    }

    @Bean
    public RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub rateLimitStub(GrpcChannelFactory channels) {
        return RateLimitGrpcServiceGrpc.newBlockingStub(channels.createChannel("ratelimit"));
    }
}
