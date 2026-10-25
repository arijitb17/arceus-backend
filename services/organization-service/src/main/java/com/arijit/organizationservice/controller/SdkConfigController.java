package com.arijit.organizationservice.controller;

import com.arijit.organizationservice.dto.SdkConfigResponse;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.service.SdkConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/projects/{projectId}/environments/{environment}/sdk-config")
@RequiredArgsConstructor
public class SdkConfigController {
    private final SdkConfigService sdkConfigService;

    @GetMapping
    public SdkConfigResponse generate(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                       @PathVariable EnvironmentType environment, Principal principal) {
        return sdkConfigService.generate(organizationId, projectId, environment, principal.getName());
    }
}
