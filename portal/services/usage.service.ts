import { gatewayClient } from "@/lib/api/gateway-client";
import type { ProjectUsageResponse } from "@/types/usage";
import type { EnvironmentType } from "@/types/project";

// Lives on monitoring-service - routed through the Gateway at
// /api/monitoring/** with an EMPTY downstream prefix rewrite (see
// gateway-service's ProxyController.Route.MONITORING), so
// /api/monitoring/projects/{id}/usage hits /projects/{id}/usage downstream.
const BASE = "/api/monitoring/projects";

export const usageService = {
  get: (organizationId: string, projectId: string, environment: EnvironmentType) =>
    gatewayClient
      .get<ProjectUsageResponse>(`${BASE}/${projectId}/usage`, { params: { organizationId, environment } })
      .then((r) => r.data),
};
