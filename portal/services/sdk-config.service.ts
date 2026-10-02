import { gatewayClient } from "@/lib/api/gateway-client";
import type { SdkConfigResponse } from "@/types/sdk-config";
import type { EnvironmentType } from "@/types/project";

const BASE = "/api/organizations";

export const sdkConfigService = {
  generate: (organizationId: string, projectId: string, environment: EnvironmentType) =>
    gatewayClient
      .get<SdkConfigResponse>(`${BASE}/${organizationId}/projects/${projectId}/environments/${environment}/sdk-config`)
      .then((r) => r.data),
};
