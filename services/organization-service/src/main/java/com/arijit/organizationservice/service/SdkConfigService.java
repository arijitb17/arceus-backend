package com.arijit.organizationservice.service;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyRequest;
import com.arijit.arceus.grpc.apikey.GetOrCreateProjectPublicKeyResponse;
import com.arijit.organizationservice.dto.SdkConfigResponse;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleStatus;
import com.arijit.organizationservice.entity.ModuleType;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.repository.ModuleEnablementRepository;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates the config a project's SDK client reads (architecture design
 * §9) - the point of the whole platform: everything upstream of this
 * (organizations, projects, module provisioning, API keys, GitHub) exists
 * so that a developer can hit this one endpoint and get something they can
 * paste into {@code new ArceusClient(config)}.
 * <p>
 * Deliberately reads only two things this service already owns
 * (ModuleEnablement's ACTIVE rows, this project's identity) plus one
 * cross-service call (a PUBLIC key from auth-service, minted on demand if
 * the project doesn't have one yet - see ApiKeyService#getOrCreateProjectPublicKey).
 * It does not call the Gateway for a live route manifest - the module base
 * routes below are the same ones registered in gateway-service's own
 * {@code ProxyController.Route} enum, which changes about as often as this
 * static map would need to; wiring an actual live "list your routes" RPC
 * for something this stable was judged not worth the extra moving part.
 * If gateway-service's routes are ever restructured, update
 * {@link #MODULE_BASE_ROUTES} to match - the two are equally sources of
 * truth today; there's no runtime-verified link between them yet, which is
 * the one honest gap here.
 */
@Service
@RequiredArgsConstructor
public class SdkConfigService {

    /** Mirrors gateway-service's ProxyController.Route enum - see class javadoc for why this isn't fetched live. */
    private static final Map<ModuleType, String> MODULE_BASE_ROUTES = new EnumMap<>(Map.of(
            ModuleType.AUTHENTICATION, "/api/auth",
            ModuleType.NOTIFICATION, "/api/notifications",
            ModuleType.CACHE, "/api/cache",
            ModuleType.SCHEDULER, "/api/scheduler",
            ModuleType.FEATURE_FLAGS, "/api/feature-flags"
    ));

    private final ModuleEnablementRepository moduleEnablementRepository;
    private final ProjectService projectService;
    private final OrgAccessGuard accessGuard;
    private final ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub apiKeyProvisioningStub;

    @Value("${arceus.organization.gateway-base-url}")
    private String gatewayBaseUrl;

    public SdkConfigResponse generate(UUID organizationId, UUID projectId, EnvironmentType environment, String callerEmail) {
        accessGuard.requireManager(organizationId, callerEmail);
        var project = projectService.findActiveOrThrow(organizationId, projectId);

        List<SdkConfigResponse.ModuleConfig> modules = moduleEnablementRepository
                .findByProjectIdAndEnvironment(projectId, environment).stream()
                .map(m -> new SdkConfigResponse.ModuleConfig(
                        m.getModule().name(),
                        m.getStatus() == ModuleStatus.ACTIVE,
                        MODULE_BASE_ROUTES.get(m.getModule())))
                .toList();

        String apiKey = resolveApiKey(organizationId, projectId, environment, callerEmail);

        return new SdkConfigResponse(projectId, project.getSlug(), environment.name(), apiKey, gatewayBaseUrl, modules);
    }

    private String resolveApiKey(UUID organizationId, UUID projectId, EnvironmentType environment, String callerEmail) {
        GetOrCreateProjectPublicKeyResponse response;
        try {
            response = apiKeyProvisioningStub.getOrCreateProjectPublicKey(GetOrCreateProjectPublicKeyRequest.newBuilder()
                    .setOrganizationId(organizationId.toString())
                    .setProjectId(projectId.toString())
                    .setEnvironment(environment.name())
                    .setRequestedBy(callerEmail)
                    .build());
        } catch (StatusRuntimeException e) {
            throw new ApiException("Could not provision an API key (auth-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (!response.getError().isEmpty()) {
            throw new ApiException("Could not provision an API key: " + response.getError(), HttpStatus.BAD_REQUEST);
        }

        if (response.getCreated()) {
            // The only time this raw key is ever available - see ApiKey's
            // javadoc in auth-service. The Developer Portal must show it to
            // the developer immediately and prominently; it cannot be
            // fetched again after this response.
            return response.getRawKey();
        }

        // A PUBLIC key already existed and auth-service correctly didn't
        // re-expose its plaintext - see getOrCreateProjectPublicKey's
        // javadoc. There is nothing usable to embed in this config; the
        // developer already has that key from when it was first created.
        return "<YOUR_EXISTING_" + response.getKeyPrefix() + "..._KEY - paste the value you saved when it was created>";
    }
}
