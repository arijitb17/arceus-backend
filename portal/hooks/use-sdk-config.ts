"use client";

import { useMutation } from "@tanstack/react-query";
import { sdkConfigService } from "@/services/sdk-config.service";
import type { EnvironmentType } from "@/types/project";

/**
 * A mutation, not a query: generating SDK config may mint a fresh API key
 * (see SdkConfigService's javadoc) - not something that should silently
 * re-run on every refetch/refocus the way a GET query normally would.
 */
export function useGenerateSdkConfig(organizationId: string, projectId: string) {
  return useMutation({
    mutationFn: (environment: EnvironmentType) => sdkConfigService.generate(organizationId, projectId, environment),
  });
}
