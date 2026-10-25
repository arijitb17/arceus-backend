package com.arijit.schedulerservice.config;

import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${arceus.scheduler.grpc-clients.feature-flag-token}")
    private String featureFlagToken;

    @Bean
    public FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub featureFlagGrpcStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, featureFlagToken);
        return FeatureFlagGrpcServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
