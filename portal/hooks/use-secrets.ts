"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { secretService } from "@/services/secret.service";
import type { CreateSecretRequest } from "@/types/secret";
import type { EnvironmentType } from "@/types/project";

function key(organizationId: string, projectId: string, environment: EnvironmentType) {
  return ["organizations", organizationId, "projects", projectId, "secrets", environment];
}

export function useSecrets(organizationId: string, projectId: string, environment: EnvironmentType) {
  return useQuery({
    queryKey: key(organizationId, projectId, environment),
    queryFn: () => secretService.list(organizationId, projectId, environment),
    enabled: !!organizationId && !!projectId,
  });
}

export function useCreateSecret(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSecretRequest) => secretService.create(organizationId, projectId, environment, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}

export function useRevokeSecret(organizationId: string, projectId: string, environment: EnvironmentType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (secretKey: string) => secretService.revoke(organizationId, projectId, environment, secretKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(organizationId, projectId, environment) }),
  });
}
