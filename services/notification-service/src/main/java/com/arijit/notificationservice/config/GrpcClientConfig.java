package com.arijit.notificationservice.config;

import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two blocking stubs:
 * <ul>
 *   <li>{@code scheduler} - used by scheduler.RetryJobRegistrar to register
 *   the recurring notification-retry job.</li>
 *   <li>{@code feature-flag} - used by featureflag.ChannelFlagGateService
 *   to gate per-channel sending behind a flag.</li>
 * </ul>
 * Both targets run their own GrpcAuthInterceptor that rejects any RPC but
 * Ping without an {@code x-internal-token} matching that target's own
 * {@code arceus.<service>.internal.api-token} - so both stubs are built
 * with that header pre-attached via {@link MetadataUtils}.
 */
@Configuration
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${arceus.notification.grpc-clients.scheduler-token}")
    private String schedulerToken;

    @Value("${arceus.notification.grpc-clients.feature-flag-token}")
    private String featureFlagToken;

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, schedulerToken);
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, featureFlagToken);
        return FeatureFlagGrpcServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
