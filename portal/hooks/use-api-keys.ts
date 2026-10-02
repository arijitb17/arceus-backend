"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { authService } from "@/services/auth.service";
import type { CreateApiKeyRequest } from "@/types/auth";
import { getErrorMessage } from "@/lib/api/error";

export function useApiKeys() {
  return useQuery({ queryKey: ["api-keys"], queryFn: authService.listApiKeys });
}

export function useCreateApiKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateApiKeyRequest) => authService.createApiKey(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useRevokeApiKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => authService.revokeApiKey(id),
    onSuccess: () => {
      toast.success("API key revoked.");
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useRevealApiKey() {
  return useMutation({
    mutationFn: (id: string) => authService.revealApiKey(id),
  });
}
