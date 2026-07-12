"use client";

import { useQuery } from "@tanstack/react-query";
import { monitoringService } from "@/services/monitoring.service";
import { deploymentService } from "@/services/deployment.service";
import { schedulerService } from "@/services/scheduler.service";
import { cacheService } from "@/services/cache.service";

const REFRESH_INTERVAL_MS = 15_000;

/** GET /dashboard - overall status, per-service health, active/recent alerts, platform metrics, resource usage. */
export function usePlatformDashboard() {
  return useQuery({
    queryKey: ["monitoring", "dashboard"],
    queryFn: monitoringService.dashboard,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}

/** GET /stats - lighter numeric summary (service counts, alert counts, availability). */
export function usePlatformStats() {
  return useQuery({
    queryKey: ["monitoring", "stats"],
    queryFn: monitoringService.stats,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}

/** GET /deployments - most recent deployments across every service/environment. */
export function useRecentDeployments(size = 5) {
  return useQuery({
    queryKey: ["deployment", "recent", size],
    queryFn: () => deploymentService.list({ page: 0, size }),
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}

/** GET /jobs/stats - live scheduler counts by status + worker pool utilization. */
export function useSchedulerStats() {
  return useQuery({
    queryKey: ["scheduler", "stats"],
    queryFn: schedulerService.stats,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}

/** GET /jobs?status=RUNNING - the jobs actively executing right now. */
export function useRunningJobs(size = 5) {
  return useQuery({
    queryKey: ["scheduler", "running-jobs", size],
    queryFn: () => schedulerService.list({ status: "RUNNING", page: 0, size }),
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}

/** GET /cache/stats - hit/miss ratio, key count, memory usage, eviction count. */
export function useCacheStats() {
  return useQuery({
    queryKey: ["cache", "stats"],
    queryFn: cacheService.stats,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
}
