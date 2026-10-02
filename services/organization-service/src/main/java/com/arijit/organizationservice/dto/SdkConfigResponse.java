package com.arijit.organizationservice.dto;

import java.util.List;
import java.util.UUID;

public record SdkConfigResponse(
        UUID projectId,
        String projectSlug,
        String environment,
        String apiKey,
        String gatewayUrl,
        List<ModuleConfig> modules
) {
    public record ModuleConfig(String module, boolean enabled, String baseRoute) {}
}
