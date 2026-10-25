package com.arijit.featureflagservice.config;

import com.arijit.arceus.grpc.cache.CacheGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
