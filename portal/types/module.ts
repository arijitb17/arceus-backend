export type ModuleType = "AUTHENTICATION" | "NOTIFICATION" | "CACHE" | "SCHEDULER" | "FEATURE_FLAGS";

export const MODULE_TYPES: ModuleType[] = ["AUTHENTICATION", "NOTIFICATION", "CACHE", "SCHEDULER", "FEATURE_FLAGS"];

export const MODULE_LABELS: Record<ModuleType, string> = {
  AUTHENTICATION: "Authentication",
  NOTIFICATION: "Notification",
  CACHE: "Cache",
  SCHEDULER: "Scheduler",
  FEATURE_FLAGS: "Feature Flags",
};

export type ModuleStatus = "PENDING" | "PROVISIONING" | "ACTIVE" | "FAILED" | "DISABLING" | "DISABLED";

/** GET/POST .../modules/{module}/enable|disable - organization-service's ModuleEnablementController. */
export interface ModuleEnablementResponse {
  id: string;
  module: ModuleType;
  environment: string;
  status: ModuleStatus;
  lastError: string | null;
  updatedAt: string;
}
