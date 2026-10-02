import { gatewayClient } from "@/lib/api/gateway-client";
import type {
  PagedResponse,
  TenantDeploymentDetailResponse,
  TenantDeploymentResponse,
  TriggerDeploymentRequest,
} from "@/types/tenant-deployment";

// Lives on deployment-service (architecture design §5) - routed through
// the Gateway at /api/deployments/** -> downstream /deployments/**.
const BASE = "/api/deployments/projects";

export const tenantDeploymentService = {
  list: (organizationId: string, projectId: string, page = 0) =>
    gatewayClient
      .get<PagedResponse<TenantDeploymentResponse>>(`${BASE}/${projectId}`, {
        params: { organizationId, page, size: 20 },
      })
      .then((r) => r.data),
  get: (organizationId: string, projectId: string, id: string) =>
    gatewayClient
      .get<TenantDeploymentDetailResponse>(`${BASE}/${projectId}/${id}`, { params: { organizationId } })
      .then((r) => r.data),
  deploy: (projectId: string, body: TriggerDeploymentRequest) =>
    gatewayClient.post<TenantDeploymentResponse>(`${BASE}/${projectId}/deploy`, body).then((r) => r.data),
  approve: (organizationId: string, projectId: string, id: string) =>
    gatewayClient
      .post<TenantDeploymentResponse>(`${BASE}/${projectId}/${id}/approve`, null, { params: { organizationId } })
      .then((r) => r.data),
};
