"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { monitoringService } from "@/services/monitoring.service";
import type { AlertRuleRequest } from "@/types/monitoring";
import { getErrorMessage } from "@/lib/api/error";

export function useServiceHealth() {
  return useQuery({
    queryKey: ["monitoring", "services"],
    queryFn: monitoringService.services,
    refetchInterval: 15_000,
  });
}

export function useMonitoringStats() {
  return useQuery({
    queryKey: ["monitoring", "stats"],
    queryFn: monitoringService.stats,
    refetchInterval: 15_000,
  });
}

export function useServiceDetail(service: string | undefined) {
  return useQuery({
    queryKey: ["monitoring", "services", "detail", service],
    queryFn: () => monitoringService.serviceDetail(service!),
    enabled: !!service,
  });
}

export function useAlertRules(page = 0, size = 20) {
  return useQuery({
    queryKey: ["monitoring", "alerts", page, size],
    queryFn: () => monitoringService.listAlertRules(page, size),
  });
}

export function useCreateAlertRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AlertRuleRequest) => monitoringService.createAlertRule(body),
    onSuccess: () => {
      toast.success("Alert rule created.");
      queryClient.invalidateQueries({ queryKey: ["monitoring", "alerts"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdateAlertRule(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AlertRuleRequest) => monitoringService.updateAlertRule(id, body),
    onSuccess: () => {
      toast.success("Alert rule updated.");
      queryClient.invalidateQueries({ queryKey: ["monitoring", "alerts"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useDeleteAlertRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => monitoringService.deleteAlertRule(id),
    onSuccess: () => {
      toast.success("Alert rule deleted.");
      queryClient.invalidateQueries({ queryKey: ["monitoring", "alerts"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
