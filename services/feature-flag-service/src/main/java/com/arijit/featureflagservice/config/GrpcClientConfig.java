package com.arijit.featureflagservice.config;

import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One blocking stub bound to the "scheduler" channel (configured under
 * spring.grpc.client.channels.scheduler in application.yml), used by
 * FlagSchedulingService to register/cancel the HTTP_CALLBACK jobs that
 * drive a flag's scheduled activation/deactivation - see the module
 * Javadoc in pom.xml for why this delegates to scheduler-service instead
 * of rolling its own {@code @Scheduled} poll loop.
 */
@Configuration
public class GrpcClientConfig {

    @Bean
    public SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerGrpcStub(GrpcChannelFactory channels) {
        return SchedulerGrpcServiceGrpc.newBlockingStub(channels.createChannel("scheduler"));
    }
}
