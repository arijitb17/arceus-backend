export interface SdkModuleConfig {
  module: string;
  enabled: boolean;
  baseRoute: string | null;
}

/** GET .../sdk-config - organization-service's SdkConfigController. The whole point of the platform - see SdkConfigService's javadoc. */
export interface SdkConfigResponse {
  projectId: string;
  projectSlug: string;
  environment: string;
  apiKey: string;
  gatewayUrl: string;
  modules: SdkModuleConfig[];
}
