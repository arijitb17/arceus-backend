package com.arijit.organizationservice.controller;

import com.arijit.organizationservice.dto.ModuleEnablementResponse;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleType;
import com.arijit.organizationservice.service.ModuleEnablementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/projects/{projectId}/environments/{environment}/modules")
@RequiredArgsConstructor
public class ModuleEnablementController {
    private final ModuleEnablementService moduleEnablementService;

    @GetMapping
    public List<ModuleEnablementResponse> list(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                                 @PathVariable EnvironmentType environment, Principal principal) {
        return moduleEnablementService.list(organizationId, projectId, environment, principal.getName()).stream()
                .map(ModuleEnablementResponse::from)
                .toList();
    }

    @PostMapping("/{module}/enable")
    public ModuleEnablementResponse enable(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                            @PathVariable EnvironmentType environment, @PathVariable ModuleType module,
                                            Principal principal) {
        return ModuleEnablementResponse.from(
                moduleEnablementService.enable(organizationId, projectId, environment, module, principal.getName()));
    }

    @PostMapping("/{module}/disable")
    public ModuleEnablementResponse disable(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                             @PathVariable EnvironmentType environment, @PathVariable ModuleType module,
                                             Principal principal) {
        return ModuleEnablementResponse.from(
                moduleEnablementService.disable(organizationId, projectId, environment, module, principal.getName()));
    }
}
