package com.arijit.featureflagservice.config;

import com.arijit.arceus.grpc.cache.CacheGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two blocking stubs:
 * <ul>
 *   <li>{@code scheduler} - used by FlagSchedulingService to register the
 *   HTTP_CALLBACK jobs that drive scheduled activation/deactivation.
 *   scheduler-service's own GrpcAuthInterceptor requires an
 *   {@code x-internal-token} on every RPC but Ping, so that stub is built
 *   with the header pre-attached via {@link MetadataUtils}.</li>
 *   <li>{@code cache} - used by FlagCacheService to store/read the
 *   flag-evaluation cache through cache-service instead of talking to
 *   Redis directly. cache-service enforces no gRPC-level auth (same trust
 *   model as auth/notification/audit/ratelimit - it trusts in-cluster
 *   callers), so no token is attached here.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final FeatureFlagProperties properties;

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, properties.grpcClients().schedulerToken());
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public CacheGrpcServiceGrpc.CacheGrpcServiceBlockingStub cacheGrpcStub(GrpcChannelFactory channels) {
        return CacheGrpcServiceGrpc.newBlockingStub(channels.createChannel("cache"));
    }
}
