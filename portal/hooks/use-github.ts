"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { githubService } from "@/services/github.service";
import type { ConnectRepoRequest } from "@/types/github";

export function useGithubConnectUrl(organizationId: string, projectId: string) {
  return useMutation({
    mutationFn: () => githubService.connectUrl(organizationId, projectId),
  });
}

export function useGithubRepositories(installationId: string | null) {
  return useQuery({
    queryKey: ["github", "installations", installationId, "repositories"],
    queryFn: () => githubService.listRepositories(installationId as string),
    enabled: !!installationId,
  });
}

export function useConnectRepo(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ConnectRepoRequest) => githubService.connect(projectId, body),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ["organizations", organizationId, "projects", projectId, "github-summary"],
      }),
  });
}

export function useDisconnectRepo(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => githubService.disconnect(projectId),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ["organizations", organizationId, "projects", projectId, "github-summary"],
      }),
  });
}
