"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { cacheService } from "@/services/cache.service";
import type { CacheRequest } from "@/types/cache";
import { getErrorMessage } from "@/lib/api/error";

export function useCacheStats() {
  return useQuery({
    queryKey: ["cache", "stats"],
    queryFn: cacheService.stats,
    refetchInterval: 15_000,
  });
}

export function useCacheLookup(namespace: string, key: string, enabled: boolean) {
  return useQuery({
    queryKey: ["cache", "entry", namespace, key],
    queryFn: () => cacheService.get(namespace, key),
    enabled,
    retry: false,
  });
}

export function useSetCacheEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CacheRequest) => cacheService.set(body),
    onSuccess: () => {
      toast.success("Cache entry saved.");
      queryClient.invalidateQueries({ queryKey: ["cache"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useDeleteCacheEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ namespace, key }: { namespace: string; key: string }) => cacheService.delete(namespace, key),
    onSuccess: () => {
      toast.success("Cache entry deleted.");
      queryClient.invalidateQueries({ queryKey: ["cache"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useClearNamespace() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (namespace: string) => cacheService.clearNamespace(namespace),
    onSuccess: () => {
      toast.success("Namespace cleared.");
      queryClient.invalidateQueries({ queryKey: ["cache"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
