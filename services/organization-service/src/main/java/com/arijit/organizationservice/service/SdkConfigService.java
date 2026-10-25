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
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SdkConfigService {
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

        log.info("SDK config generated: project={} env={} modules={} by={}", projectId, environment, modules.size(), LogMasker.email(callerEmail));
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
            log.error("auth-service getOrCreateProjectPublicKey failed: project={} env={} status={}", projectId, environment, e.getStatus(), e);
            throw new ApiException("Could not provision an API key (auth-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (!response.getError().isEmpty()) {
            log.warn("auth-service refused to provision SDK key: project={} env={} error={}", projectId, environment, response.getError());
            throw new ApiException("Could not provision an API key: " + response.getError(), HttpStatus.BAD_REQUEST);
        }

        if (response.getCreated()) {
            return response.getRawKey();
        }

        return "<YOUR_EXISTING_" + response.getKeyPrefix() + "..._KEY - paste the value you saved when it was created>";
    }
}
