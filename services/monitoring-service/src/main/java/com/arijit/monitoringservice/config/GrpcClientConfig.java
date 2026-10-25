package com.arijit.monitoringservice.config;

import com.arijit.arceus.grpc.organization.OrganizationAccessGrpcServiceGrpc;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    @Bean
    public OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceBlockingStub organizationAccessStub(GrpcChannelFactory channels) {
        return OrganizationAccessGrpcServiceGrpc.newBlockingStub(channels.createChannel("organization"));
    }
}
