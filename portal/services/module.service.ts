import { gatewayClient } from "@/lib/api/gateway-client";
import type { ModuleEnablementResponse, ModuleType } from "@/types/module";
import type { EnvironmentType } from "@/types/project";

const BASE = "/api/organizations";

export const moduleService = {
  list: (organizationId: string, projectId: string, environment: EnvironmentType) =>
    gatewayClient
      .get<ModuleEnablementResponse[]>(
        `${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/modules`
      )
      .then((r) => r.data),
  enable: (organizationId: string, projectId: string, environment: EnvironmentType, module: ModuleType) =>
    gatewayClient
      .post<ModuleEnablementResponse>(
        `${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/modules/${module}/enable`
      )
      .then((r) => r.data),
  disable: (organizationId: string, projectId: string, environment: EnvironmentType, module: ModuleType) =>
    gatewayClient
      .post<ModuleEnablementResponse>(
        `${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/modules/${module}/disable`
      )
      .then((r) => r.data),
};
