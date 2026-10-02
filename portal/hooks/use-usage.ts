"use client";

import { useQuery } from "@tanstack/react-query";
import { usageService } from "@/services/usage.service";
import type { EnvironmentType } from "@/types/project";

export function useProjectUsage(organizationId: string, projectId: string, environment: EnvironmentType) {
  return useQuery({
    queryKey: ["organizations", organizationId, "projects", projectId, "usage", environment],
    queryFn: () => usageService.get(organizationId, projectId, environment),
    enabled: !!organizationId && !!projectId,
  });
}
