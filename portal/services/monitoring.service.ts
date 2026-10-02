import { gatewayClient } from "@/lib/api/gateway-client";
import type {
  AlertRuleRequest,
  AlertRuleResponse,
  DashboardResponse,
  PlatformHealthResponse,
  ServiceDetailResponse,
  ServiceMetricsDetailResponse,
  ServiceMetricsDto,
  StatsResponse,
} from "@/types/monitoring";
import type { PagedResponse } from "@/types/api";

// Routed through the Gateway at /api/monitoring/** (downstream prefix is
// empty - monitoring-service's own controllers have no shared path prefix,
// so /api/monitoring/dashboard -> /dashboard, /api/monitoring/stats -> /stats, etc.)
const BASE = "/api/monitoring";

export const monitoringService = {
  dashboard: () => gatewayClient.get<DashboardResponse>(`${BASE}/dashboard`).then((r) => r.data),
  stats: () => gatewayClient.get<StatsResponse>(`${BASE}/stats`).then((r) => r.data),
  services: () => gatewayClient.get<PlatformHealthResponse>(`${BASE}/services`).then((r) => r.data),
  serviceDetail: (service: string) =>
    gatewayClient.get<ServiceDetailResponse>(`${BASE}/services/${service}`).then((r) => r.data),
  metrics: () => gatewayClient.get<ServiceMetricsDto[]>(`${BASE}/metrics`).then((r) => r.data),
  serviceMetrics: (service: string) =>
    gatewayClient.get<ServiceMetricsDetailResponse>(`${BASE}/metrics/${service}`).then((r) => r.data),

  listAlertRules: (page = 0, size = 20) =>
    gatewayClient
      .get<PagedResponse<AlertRuleResponse>>(`${BASE}/alerts`, { params: { page, size } })
      .then((r) => r.data),
  createAlertRule: (body: AlertRuleRequest) =>
    gatewayClient.post<AlertRuleResponse>(`${BASE}/alerts`, body).then((r) => r.data),
  updateAlertRule: (id: string, body: AlertRuleRequest) =>
    gatewayClient.put<AlertRuleResponse>(`${BASE}/alerts/${id}`, body).then((r) => r.data),
  deleteAlertRule: (id: string) => gatewayClient.delete<void>(`${BASE}/alerts/${id}`),
};
