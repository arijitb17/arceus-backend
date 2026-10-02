"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { moduleService } from "@/services/module.service";
import type { ModuleType } from "@/types/module";
import type { EnvironmentType } from "@/types/project";

function key(organizationId: string, projectId: string, environment: EnvironmentType) {
  return ["organizations", organizationId, "projects", projectId, "modules", environment];
}

export function useModules(organizationId: string, projectId: string, environment: EnvironmentType) {
  return useQuery({
    queryKey: key(organizationId, projectId, environment),
    queryFn: () => moduleService.list(organizationId, projectId, environment),
    enabled: !!organizationId && !!projectId,
    // enable()/disable() resolve straight to a terminal status in the same
    // request (see ModuleProvisioningClient), and useEnableModule/
    // useDisableModule already invalidate this query on success - so this
    // poll is a defensive fallback, not the primary way the UI updates.
    refetchInterval: (query) => {
      const data = query.state.data;
      const pending = data?.some((m) => m.status === "PENDING" || m.status === "PROVISIONING" || m.status === "DISABLING");
      return pending ? 3000 : false;
    },
  });
}

export function useEnableModule(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (module: ModuleType) => moduleService.enable(organizationId, projectId, environment, module),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}

export function useDisableModule(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (module: ModuleType) => moduleService.disable(organizationId, projectId, environment, module),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}
