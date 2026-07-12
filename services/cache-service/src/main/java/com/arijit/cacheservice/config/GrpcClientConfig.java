package com.arijit.cacheservice.config;

import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One blocking stub bound to the "scheduler" channel, used by
 * scheduler.SweepJobRegistrar to register the recurring cache-sweep job -
 * see CacheSweepService's Javadoc.
 * <p>
 * scheduler-service's own GrpcAuthInterceptor rejects any RPC but Ping
 * without an {@code x-internal-token} matching its
 * {@code arceus.scheduler.internal.api-token} - the stub is built with
 * that header pre-attached via {@link MetadataUtils}, same pattern as
 * every other cross-service caller on the platform.
 */
@Configuration
@RequiredArgsConstructor
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final CacheProperties properties;

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, properties.scheduling().schedulerToken());
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
