package com.arijit.organizationservice.config;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.tenant.AuthTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.CacheTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.FeatureFlagTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.NotificationTenantProvisioningServiceGrpc;
import com.arijit.arceus.grpc.tenant.SchedulerTenantProvisioningServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * organization-service's outgoing gRPC calls. auth-service trusts its
 * caller's network boundary the same way organization-service does (see
 * OrganizationGatewayGrpcService), so no token is attached to that stub.
 * The five ModuleType-owning services below are called directly by
 * ModuleEnablementService to provision/deprovision a module - see that
 * class's javadoc for why this replaced the old deployment-service event
 * pipeline. auth/notification/cache trust the network boundary the same
 * way; scheduler/feature-flag run a global GrpcAuthInterceptor requiring
 * x-internal-token on every RPC, same pattern as gateway-service's own
 * GrpcClientConfig.
 */
@Configuration
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${arceus.organization.grpc-clients.scheduler-token}")
    private String schedulerToken;

    @Value("${arceus.organization.grpc-clients.feature-flag-token}")
    private String featureFlagToken;

    @Bean
    public ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub apiKeyProvisioningStub(GrpcChannelFactory channels) {
        return ApiKeyProvisioningGrpcServiceGrpc.newBlockingStub(channels.createChannel("auth"));
    }

    @Bean
    public AuthTenantProvisioningServiceGrpc.AuthTenantProvisioningServiceBlockingStub authTenantProvisioningStub(GrpcChannelFactory channels) {
        return AuthTenantProvisioningServiceGrpc.newBlockingStub(channels.createChannel("auth"));
    }

    @Bean
    public NotificationTenantProvisioningServiceGrpc.NotificationTenantProvisioningServiceBlockingStub notificationTenantProvisioningStub(GrpcChannelFactory channels) {
        return NotificationTenantProvisioningServiceGrpc.newBlockingStub(channels.createChannel("notification"));
    }

    @Bean
    public CacheTenantProvisioningServiceGrpc.CacheTenantProvisioningServiceBlockingStub cacheTenantProvisioningStub(GrpcChannelFactory channels) {
        return CacheTenantProvisioningServiceGrpc.newBlockingStub(channels.createChannel("cache"));
    }

    @Bean
    public SchedulerTenantProvisioningServiceGrpc.SchedulerTenantProvisioningServiceBlockingStub schedulerTenantProvisioningStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, schedulerToken);
        return SchedulerTenantProvisioningServiceGrpc.newBlockingStub(channels.createChannel("scheduler"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Bean
    public FeatureFlagTenantProvisioningServiceGrpc.FeatureFlagTenantProvisioningServiceBlockingStub featureFlagTenantProvisioningStub(GrpcChannelFactory channels) {
        Metadata metadata = new Metadata();
        metadata.put(TOKEN_HEADER, featureFlagToken);
        return FeatureFlagTenantProvisioningServiceGrpc.newBlockingStub(channels.createChannel("feature-flag"))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
