"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { projectService } from "@/services/project.service";
import type { CreateProjectRequest } from "@/types/project";

export function useProjects(organizationId: string) {
  return useQuery({
    queryKey: ["organizations", organizationId, "projects"],
    queryFn: () => projectService.list(organizationId),
    enabled: !!organizationId,
  });
}

export function useProject(organizationId: string, projectId: string) {
  return useQuery({
    queryKey: ["organizations", organizationId, "projects", projectId],
    queryFn: () => projectService.get(organizationId, projectId),
    enabled: !!organizationId && !!projectId,
  });
}

export function useCreateProject(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectRequest) => projectService.create(organizationId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "projects"] }),
  });
}

export function useProjectEnvironments(organizationId: string, projectId: string) {
  return useQuery({
    queryKey: ["organizations", organizationId, "projects", projectId, "environments"],
    queryFn: () => projectService.environments(organizationId, projectId),
    enabled: !!organizationId && !!projectId,
  });
}
