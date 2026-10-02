"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { featureFlagService, type ListFlagsParams } from "@/services/feature-flag.service";
import type { CreateFlagRequest, FlagEnvironment, UpdateFlagRequest } from "@/types/feature-flag";
import { getErrorMessage } from "@/lib/api/error";

export function useFlags(params: ListFlagsParams) {
  return useQuery({
    queryKey: ["feature-flags", params],
    queryFn: () => featureFlagService.list(params),
  });
}

export function useFlagHistory(key: string | undefined) {
  return useQuery({
    queryKey: ["feature-flags", "history", key],
    queryFn: () => featureFlagService.history(key),
    enabled: !!key,
  });
}

export function useCreateFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateFlagRequest) => featureFlagService.create(body),
    onSuccess: () => {
      toast.success("Flag created.");
      queryClient.invalidateQueries({ queryKey: ["feature-flags"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdateFlag(key: string, environment: FlagEnvironment) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateFlagRequest) => featureFlagService.update(key, body, environment),
    onSuccess: () => {
      toast.success("Flag updated.");
      queryClient.invalidateQueries({ queryKey: ["feature-flags"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useToggleFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, environment, enabled }: { key: string; environment: FlagEnvironment; enabled: boolean }) =>
      enabled ? featureFlagService.enable(key, environment) : featureFlagService.disable(key, environment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["feature-flags"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useDeleteFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, environment }: { key: string; environment: FlagEnvironment }) =>
      featureFlagService.delete(key, environment),
    onSuccess: () => {
      toast.success("Flag deleted.");
      queryClient.invalidateQueries({ queryKey: ["feature-flags"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
