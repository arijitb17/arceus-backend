import { gatewayClient } from "@/lib/api/gateway-client";
import type { JobResponse, JobStatsResponse } from "@/types/scheduler";
import type { PagedResponse } from "@/types/api";

// Routed through the Gateway at /api/scheduler/** - downstream prefix is
// /jobs (scheduler-service's own controller), so e.g. /api/scheduler/stats
// maps to /jobs/stats, /api/scheduler/{id}/pause maps to /jobs/{id}/pause.
const BASE = "/api/scheduler";

export interface ListJobsParams {
  serviceName?: string;
  status?: string;
  page?: number;
  size?: number;
}

export const schedulerService = {
  list: (params: ListJobsParams = {}) =>
    gatewayClient.get<PagedResponse<JobResponse>>(BASE, { params }).then((r) => r.data),
  get: (id: string) => gatewayClient.get<JobResponse>(`${BASE}/${id}`).then((r) => r.data),
  stats: () => gatewayClient.get<JobStatsResponse>(`${BASE}/stats`).then((r) => r.data),
  pause: (id: string) => gatewayClient.post<JobResponse>(`${BASE}/${id}/pause`).then((r) => r.data),
  resume: (id: string) => gatewayClient.post<JobResponse>(`${BASE}/${id}/resume`).then((r) => r.data),
  cancel: (id: string) => gatewayClient.post<JobResponse>(`${BASE}/${id}/cancel`).then((r) => r.data),
  retry: (id: string) => gatewayClient.post<JobResponse>(`${BASE}/${id}/retry`).then((r) => r.data),
};
