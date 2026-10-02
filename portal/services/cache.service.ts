import { gatewayClient } from "@/lib/api/gateway-client";
import type { CacheRequest, CacheResponse, CacheStatsResponse } from "@/types/cache";

// Routed through the Gateway at /api/cache/** - downstream prefix is also
// /cache, so paths here match cache-service's own controller 1:1. There is
// no "list all keys" endpoint - entries can only be looked up by exact
// namespace + key, or cleared by namespace/pattern.
const BASE = "/api/cache";

export const cacheService = {
  stats: () => gatewayClient.get<CacheStatsResponse>(`${BASE}/stats`).then((r) => r.data),
  get: (namespace: string, key: string) =>
    gatewayClient.get<CacheResponse>(`${BASE}/${namespace}/${key}`).then((r) => r.data),
  set: (body: CacheRequest) => gatewayClient.post<CacheResponse>(BASE, body).then((r) => r.data),
  update: (namespace: string, key: string, body: CacheRequest) =>
    gatewayClient.put<CacheResponse>(`${BASE}/${namespace}/${key}`, body).then((r) => r.data),
  delete: (namespace: string, key: string) => gatewayClient.delete<void>(`${BASE}/${namespace}/${key}`),
  exists: (namespace: string, key: string) =>
    gatewayClient.get<boolean>(`${BASE}/${namespace}/${key}/exists`).then((r) => r.data),
  clearNamespace: (namespace: string) => gatewayClient.delete<void>(`${BASE}/namespace/${namespace}`),
  clearPattern: (pattern: string) => gatewayClient.delete<void>(`${BASE}/pattern/${pattern}`),
};
