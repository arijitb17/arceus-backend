"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { projectApiKeyService } from "@/services/project-api-key.service";
import type { CreateProjectApiKeyRequest } from "@/types/project-api-key";
import type { EnvironmentType } from "@/types/project";

function key(organizationId: string, projectId: string, environment: EnvironmentType) {
  return ["organizations", organizationId, "projects", projectId, "api-keys", environment];
}

export function useProjectApiKeys(organizationId: string, projectId: string, environment: EnvironmentType) {
  return useQuery({
    queryKey: key(organizationId, projectId, environment),
    queryFn: () => projectApiKeyService.list(organizationId, projectId, environment),
    enabled: !!organizationId && !!projectId,
  });
}

export function useCreateProjectApiKey(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectApiKeyRequest) => projectApiKeyService.create(organizationId, projectId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}

export function useRevokeProjectApiKey(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => projectApiKeyService.revoke(organizationId, projectId, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}

export function useRevealProjectApiKey(organizationId: string, projectId: string) {
  return useMutation({
    mutationFn: (id: string) => projectApiKeyService.reveal(organizationId, projectId, id),
  });
}
