import { gatewayClient } from "@/lib/api/gateway-client";
import type { PagedResponse } from "@/types/api";
import type {
  CreateFlagRequest,
  FlagEnvironment,
  FlagHistoryResponse,
  FlagResponse,
  FlagStatus,
  UpdateFlagRequest,
} from "@/types/feature-flag";

// Routed through the Gateway at /api/feature-flags/** - downstream prefix
// is /flags (feature-flag-service's own controller). Flags are identified
// by `key` + `environment` (default GLOBAL), not a synthetic id, in every
// mutation endpoint.
const BASE = "/api/feature-flags";

export interface ListFlagsParams {
  environment?: FlagEnvironment;
  status?: FlagStatus;
  page?: number;
  size?: number;
}

export const featureFlagService = {
  list: (params: ListFlagsParams = {}) =>
    gatewayClient.get<PagedResponse<FlagResponse>>(BASE, { params }).then((r) => r.data),
  get: (key: string, environment: FlagEnvironment = "GLOBAL") =>
    gatewayClient.get<FlagResponse>(`${BASE}/${key}`, { params: { environment } }).then((r) => r.data),
  create: (body: CreateFlagRequest) => gatewayClient.post<FlagResponse>(BASE, body).then((r) => r.data),
  update: (key: string, body: UpdateFlagRequest, environment: FlagEnvironment = "GLOBAL") =>
    gatewayClient.put<FlagResponse>(`${BASE}/${key}`, body, { params: { environment } }).then((r) => r.data),
  enable: (key: string, environment: FlagEnvironment = "GLOBAL", actor?: string) =>
    gatewayClient.post<FlagResponse>(`${BASE}/${key}/enable`, null, { params: { environment, actor } }).then((r) => r.data),
  disable: (key: string, environment: FlagEnvironment = "GLOBAL", actor?: string) =>
    gatewayClient.post<FlagResponse>(`${BASE}/${key}/disable`, null, { params: { environment, actor } }).then((r) => r.data),
  delete: (key: string, environment: FlagEnvironment = "GLOBAL", actor?: string) =>
    gatewayClient.delete<void>(`${BASE}/${key}`, { params: { environment, actor } }),
  history: (key?: string, page = 0, size = 20) =>
    gatewayClient
      .get<PagedResponse<FlagHistoryResponse>>(`${BASE}/history`, { params: { key, page, size } })
      .then((r) => r.data),
};
