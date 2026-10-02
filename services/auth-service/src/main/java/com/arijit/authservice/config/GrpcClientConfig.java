package com.arijit.authservice.config;

import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.arceus.grpc.organization.OrganizationAccessGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Three blocking stubs:
 * <ul>
 *   <li>{@code scheduler} - used by scheduler.SessionCleanupJobRegistrar to
 *   register the recurring expired-session-cleanup job.</li>
 *   <li>{@code feature-flag} - used by featureflag.FeatureFlagGateService
 *   to gate configurable auth actions (e.g. registration) behind a flag.</li>
 *   <li>{@code organization} - used by ApiKeyService#createProjectKey to
 *   check the caller is an OWNER/ADMIN of the target organization before
 *   issuing a project-scoped key (architecture design §7), without
 *   duplicating organization-service's own OrgRole logic here.</li>
 * </ul>
 * The first two targets run their own GrpcAuthInterceptor requiring an
 * {@code x-internal-token}; organization-service does not (it trusts its
 * caller's network boundary the same way auth-service's own Gateway
 * forwarding RPC does), so that stub is built with no token attached.
 */
@Configuration
public class GrpcClientConfig {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${app.scheduling.scheduler-token}")
    private String schedulerToken;

    @Value("${app.feature-flags.token}")
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

    @Bean
    public OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceBlockingStub organizationAccessStub(GrpcChannelFactory channels) {
        return OrganizationAccessGrpcServiceGrpc.newBlockingStub(channels.createChannel("organization"));
    }
}
