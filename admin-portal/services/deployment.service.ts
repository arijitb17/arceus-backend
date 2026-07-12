import { gatewayClient } from "@/lib/api/gateway-client";
import type { CreateDeploymentRequest, DeploymentResponse, RollbackRequest } from "@/types/deployment";
import type { PagedResponse } from "@/types/api";

// Routed through the Gateway at /api/deployments/** (downstream prefix is
// also /deployments, so paths here match deployment-service's own controller 1:1).
const BASE = "/api/deployments";

export interface ListDeploymentsParams {
  serviceName?: string;
  environment?: string;
  status?: string;
  page?: number;
  size?: number;
}

export const deploymentService = {
  list: (params: ListDeploymentsParams = {}) =>
    gatewayClient.get<PagedResponse<DeploymentResponse>>(BASE, { params }).then((r) => r.data),
  history: (params: ListDeploymentsParams = {}) =>
    gatewayClient.get<PagedResponse<DeploymentResponse>>(`${BASE}/history`, { params }).then((r) => r.data),
  get: (id: string) => gatewayClient.get<DeploymentResponse>(`${BASE}/${id}`).then((r) => r.data),
  create: (body: CreateDeploymentRequest) =>
    gatewayClient.post<DeploymentResponse>(BASE, body).then((r) => r.data),
  approve: (id: string) => gatewayClient.post<DeploymentResponse>(`${BASE}/${id}/approve`).then((r) => r.data),
  deploy: (id: string) => gatewayClient.post<DeploymentResponse>(`${BASE}/${id}/deploy`).then((r) => r.data),
  rollback: (id: string, body: RollbackRequest) =>
    gatewayClient.post<DeploymentResponse>(`${BASE}/${id}/rollback`, body).then((r) => r.data),
  cancel: (id: string) => gatewayClient.post<DeploymentResponse>(`${BASE}/${id}/cancel`).then((r) => r.data),
};
