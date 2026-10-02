"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { schedulerService, type ListJobsParams } from "@/services/scheduler.service";
import { getErrorMessage } from "@/lib/api/error";

export function useJobs(params: ListJobsParams) {
  return useQuery({
    queryKey: ["scheduler", "jobs", params],
    queryFn: () => schedulerService.list(params),
    refetchInterval: 10_000,
  });
}

export function useJobStats() {
  return useQuery({
    queryKey: ["scheduler", "stats"],
    queryFn: schedulerService.stats,
    refetchInterval: 15_000,
  });
}

export function useJob(id: string | undefined) {
  return useQuery({
    queryKey: ["scheduler", "jobs", "detail", id],
    queryFn: () => schedulerService.get(id!),
    enabled: !!id,
    refetchInterval: 10_000,
  });
}

function useJobAction(action: (id: string) => Promise<unknown>, successMessage: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => action(id),
    onSuccess: () => {
      toast.success(successMessage);
      queryClient.invalidateQueries({ queryKey: ["scheduler"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function usePauseJob() {
  return useJobAction(schedulerService.pause, "Job paused.");
}
export function useResumeJob() {
  return useJobAction(schedulerService.resume, "Job resumed.");
}
export function useCancelJob() {
  return useJobAction(schedulerService.cancel, "Job cancelled.");
}
export function useRetryJob() {
  return useJobAction(schedulerService.retry, "Job queued for retry.");
}
