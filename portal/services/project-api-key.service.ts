import { gatewayClient } from "@/lib/api/gateway-client";
import type { CreateProjectApiKeyRequest, ProjectApiKeyCreatedResponse, ProjectApiKeyResponse, RevealedApiKeyResponse } from "@/types/project-api-key";
import type { EnvironmentType } from "@/types/project";

// Lives on auth-service (§7 of the architecture design), not organization-service -
// API keys remain auth-service's own resource.
const BASE = "/api/auth/organizations";

export const projectApiKeyService = {
  list: (organizationId: string, projectId: string, environment: EnvironmentType) =>
    gatewayClient
      .get<ProjectApiKeyResponse[]>(`${BASE}/${organizationId}/projects/${projectId}/api-keys`, { params: { environment } })
      .then((r) => r.data),
  create: (organizationId: string, projectId: string, body: CreateProjectApiKeyRequest) =>
    gatewayClient
      .post<ProjectApiKeyCreatedResponse>(`${BASE}/${organizationId}/projects/${projectId}/api-keys`, body)
      .then((r) => r.data),
  reveal: (organizationId: string, projectId: string, id: string) =>
    gatewayClient
      .get<RevealedApiKeyResponse>(`${BASE}/${organizationId}/projects/${projectId}/api-keys/${id}/reveal`)
      .then((r) => r.data.key),
  revoke: (organizationId: string, projectId: string, id: string) =>
    gatewayClient.delete<void>(`${BASE}/${organizationId}/projects/${projectId}/api-keys/${id}`),
};
