package com.arijit.cacheservice.config;

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

    private final CacheProperties properties;

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, properties.scheduling().schedulerToken());
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
