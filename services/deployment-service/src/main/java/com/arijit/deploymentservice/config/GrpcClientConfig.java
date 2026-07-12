package com.arijit.deploymentservice.config;

import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.arceus.grpc.monitoring.MonitoringGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Three blocking stubs bound to channels configured under
 * {@code spring.grpc.client.channels.*} in application.yml:
 * <ul>
 *   <li>{@code monitoring} - used by DeploymentHealthVerificationService for
 *   pre/post-deploy health checks, instead of this service re-implementing
 *   monitoring-service's own Actuator polling.</li>
 *   <li>{@code scheduler} - used by DeploymentSchedulingService to register
 *   the HTTP_CALLBACK job behind a scheduled deployment, same pattern as
 *   feature-flag-service's GrpcClientConfig/FlagSchedulingService.</li>
 *   <li>{@code feature-flag} - used by FeatureFlagEnablementService to
 *   enable a deployment's associated flag once it reaches RUNNING.</li>
 * </ul>
 * <p>
 * Every one of those three targets runs its own GrpcAuthInterceptor that
 * rejects any RPC (other than Ping) missing an {@code x-internal-token}
 * metadata entry matching that target's own
 * {@code arceus.<service>.internal.api-token} - so every stub here is
 * built with that header pre-attached via {@link MetadataUtils}. Without
 * this, every call below would fail closed with UNAUTHENTICATED - which,
 * before this fix, they silently did (DeploymentHealthVerificationService
 * treats a gRPC failure as a warning, not an error, so pre/post-deploy
 * health verification was never actually running).
 */
@Configuration
@RequiredArgsConstructor
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final DeploymentProperties properties;

    @Bean
    public MonitoringGrpcServiceGrpc.MonitoringGrpcServiceBlockingStub monitoringGrpcStub(GrpcChannelFactory channels) {
        return MonitoringGrpcServiceGrpc.newBlockingStub(channels.createChannel("monitoring"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(tokenMetadata(properties.grpcClients().monitoringToken())));
    }

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(tokenMetadata(properties.grpcClients().schedulerToken())));
    }

    @Bean
    public FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagGrpcStub(GrpcChannelFactory channels) {
        return FeatureFlagGrpcServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(tokenMetadata(properties.grpcClients().featureFlagToken())));
    }

    private static Metadata tokenMetadata(String token) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, token);
        return metadata;
    }
}
