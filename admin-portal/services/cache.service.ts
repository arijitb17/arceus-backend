import { gatewayClient } from "@/lib/api/gateway-client";
import type { CacheStatsResponse } from "@/types/cache";

// Routed through the Gateway at /api/cache/** - downstream prefix is also
// /cache, so paths here match cache-service's own controller 1:1.
const BASE = "/api/cache";

export const cacheService = {
  stats: () => gatewayClient.get<CacheStatsResponse>(`${BASE}/stats`).then((r) => r.data),
};
