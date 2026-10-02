"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { tenantDeploymentService } from "@/services/tenant-deployment.service";
import type { EnvironmentType } from "@/types/project";

function key(organizationId: string, projectId: string) {
  return ["organizations", organizationId, "projects", projectId, "deployments"];
}

export function useTenantDeployments(organizationId: string, projectId: string, page = 0) {
  return useQuery({
    queryKey: [...key(organizationId, projectId), page],
    queryFn: () => tenantDeploymentService.list(organizationId, projectId, page),
    enabled: !!organizationId && !!projectId,
    // A build/deploy in flight (BUILD_QUEUED/BUILDING/DEPLOYING) is the
    // whole reason this tab exists to be watched live - poll while any row
    // on the current page is still moving, same pattern as the Modules tab.
    refetchInterval: (query) => {
      const data = query.state.data;
      const inFlight = data?.items.some((d) =>
        ["BUILD_QUEUED", "BUILDING", "DEPLOYING"].includes(d.status)
      );
      return inFlight ? 3000 : false;
    },
  });
}

export function useTenantDeploymentDetail(organizationId: string, projectId: string, id: string | null) {
  return useQuery({
    queryKey: [...key(organizationId, projectId), id],
    queryFn: () => tenantDeploymentService.get(organizationId, projectId, id as string),
    enabled: !!organizationId && !!projectId && !!id,
  });
}

export function useTriggerDeployment(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (environment: EnvironmentType) => tenantDeploymentService.deploy(projectId, { organizationId, environment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId) }),
  });
}

export function useApproveDeployment(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => tenantDeploymentService.approve(organizationId, projectId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId) }),
  });
}
