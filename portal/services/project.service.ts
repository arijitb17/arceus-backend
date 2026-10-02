import { gatewayClient } from "@/lib/api/gateway-client";
import type { CreateProjectRequest, EnvironmentSummary, ProjectResponse } from "@/types/project";

const BASE = "/api/organizations";

export const projectService = {
  list: (organizationId: string) =>
    gatewayClient.get<ProjectResponse[]>(`${BASE}/${organizationId}/projects`).then((r) => r.data),
  get: (organizationId: string, projectId: string) =>
    gatewayClient.get<ProjectResponse>(`${BASE}/${organizationId}/projects/${projectId}`).then((r) => r.data),
  create: (organizationId: string, body: CreateProjectRequest) =>
    gatewayClient.post<ProjectResponse>(`${BASE}/${organizationId}/projects`, body).then((r) => r.data),
  delete: (organizationId: string, projectId: string) =>
    gatewayClient.delete<void>(`${BASE}/${organizationId}/projects/${projectId}`),
  environments: (organizationId: string, projectId: string) =>
    gatewayClient
      .get<EnvironmentSummary[]>(`${BASE}/${organizationId}/projects/${projectId}/environments`)
      .then((r) => r.data),
};
