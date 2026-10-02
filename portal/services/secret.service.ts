import { gatewayClient } from "@/lib/api/gateway-client";
import type { CreateSecretRequest, SecretResponse } from "@/types/secret";
import type { EnvironmentType } from "@/types/project";

const BASE = "/api/organizations";

export const secretService = {
  list: (organizationId: string, projectId: string, environment: EnvironmentType) =>
    gatewayClient
      .get<SecretResponse[]>(`${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/secrets`)
      .then((r) => r.data),
  create: (organizationId: string, projectId: string, environment: EnvironmentType, body: CreateSecretRequest) =>
    gatewayClient
      .post<SecretResponse>(`${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/secrets`, body)
      .then((r) => r.data),
  revoke: (organizationId: string, projectId: string, environment: EnvironmentType, secretKey: string) =>
    gatewayClient.delete<void>(
      `${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/secrets/${encodeURIComponent(secretKey)}`
    ),
};
