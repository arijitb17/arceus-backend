package com.arijit.gatewayservice.config;

import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.arceus.grpc.gateway.AuditGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.AuthGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.CacheGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.DeploymentGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.FeatureFlagGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.MonitoringGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.NotificationGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.SchedulerGatewayServiceGrpc;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;

/**
 * Wires up one blocking stub per downstream service, each bound to the
 * named channel configured under spring.grpc.client.* in application.yml.
 * Keeping the channel name ("auth", "notification", "audit", "ratelimit",
 * "feature-flag", "monitoring", "deployment", "scheduler", "cache") as the
 * single point of truth for where each service lives means routing code
 * never hardcodes a host/port.
 * <p>
 * auth/notification/audit/ratelimit/cache trust the Gateway as their edge
 * and enforce no gRPC-level token. monitoring/deployment/scheduler/
 * feature-flag are different: each runs a {@code @GrpcGlobalServerInterceptor}
 * that requires {@code x-internal-token} on every RPC but {@code Ping} -
 * including their *Gateway RPC (Forward), not just their domain-specific
 * ones, since the interceptor is global to the whole gRPC server. So all
 * four of those stubs, not just the feature-flag evaluation one used by
 * RouteFlagGatewayClient, need their target's token attached - see
 * deployment-service's GrpcClientConfig for the fuller explanation of this
 * pattern.
 */
@Configuration
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${arceus.gateway.grpc-clients.feature-flag-token}")
    private String featureFlagToken;

    @Value("${arceus.gateway.grpc-clients.monitoring-token}")
    private String monitoringToken;

    @Value("${arceus.gateway.grpc-clients.deployment-token}")
    private String deploymentToken;

    @Value("${arceus.gateway.grpc-clients.scheduler-token}")
    private String schedulerToken;

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
    public MonitoringGatewayServiceGrpc.MonitoringGatewayServiceBlockingStub monitoringGatewayStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, monitoringToken);
        return MonitoringGatewayServiceGrpc.newBlockingStub(channels.createChannel("monitoring"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public DeploymentGatewayServiceGrpc.DeploymentGatewayServiceBlockingStub deploymentGatewayStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, deploymentToken);
        return DeploymentGatewayServiceGrpc.newBlockingStub(channels.createChannel("deployment"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public SchedulerGatewayServiceGrpc.SchedulerGatewayServiceBlockingStub schedulerGatewayStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, schedulerToken);
        return SchedulerGatewayServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public CacheGatewayServiceGrpc.CacheGatewayServiceBlockingStub cacheGatewayStub(GrpcChannelFactory channels) {
        return CacheGatewayServiceGrpc.newBlockingStub(channels.createChannel("cache"));
    }

    @Bean
    public RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub rateLimitStub(GrpcChannelFactory channels) {
        return RateLimitGrpcServiceGrpc.newBlockingStub(channels.createChannel("ratelimit"));
    }

    @Bean
    public FeatureFlagGatewayServiceGrpc.FeatureFlagGatewayServiceBlockingStub featureFlagGatewayStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, featureFlagToken);
        return FeatureFlagGatewayServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, featureFlagToken);
        return FeatureFlagGrpcServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
