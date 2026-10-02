"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { deploymentService, type ListDeploymentsParams } from "@/services/deployment.service";
import type { CreateDeploymentRequest, RollbackRequest } from "@/types/deployment";
import { getErrorMessage } from "@/lib/api/error";

export function useDeployments(params: ListDeploymentsParams) {
  return useQuery({
    queryKey: ["deployments", params],
    queryFn: () => deploymentService.list(params),
    refetchInterval: 10_000,
  });
}

export function useDeployment(id: string | undefined) {
  return useQuery({
    queryKey: ["deployments", "detail", id],
    queryFn: () => deploymentService.get(id!),
    enabled: !!id,
    refetchInterval: 10_000,
  });
}

export function useCreateDeployment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDeploymentRequest) => deploymentService.create(body),
    onSuccess: () => {
      toast.success("Deployment created.");
      queryClient.invalidateQueries({ queryKey: ["deployments"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

function useDeploymentAction(action: (id: string) => Promise<unknown>, successMessage: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => action(id),
    onSuccess: () => {
      toast.success(successMessage);
      queryClient.invalidateQueries({ queryKey: ["deployments"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useApproveDeployment() {
  return useDeploymentAction(deploymentService.approve, "Deployment approved.");
}
export function useDeployDeployment() {
  return useDeploymentAction(deploymentService.deploy, "Deployment started.");
}
export function useCancelDeployment() {
  return useDeploymentAction(deploymentService.cancel, "Deployment cancelled.");
}

export function useRollbackDeployment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: RollbackRequest }) => deploymentService.rollback(id, body),
    onSuccess: () => {
      toast.success("Rollback initiated.");
      queryClient.invalidateQueries({ queryKey: ["deployments"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
