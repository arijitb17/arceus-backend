export type FlagType = "BOOLEAN" | "INTEGER" | "LONG" | "DOUBLE" | "STRING" | "JSON";
export type FlagEnvironment = "DEVELOPMENT" | "STAGING" | "PRODUCTION" | "GLOBAL";
export type FlagStatus = "ENABLED" | "DISABLED";
export type TargetType = "GLOBAL" | "ENVIRONMENT" | "USER" | "ROLE" | "TENANT";

export interface FlagResponse {
  id: string;
  key: string;
  name: string;
  description: string | null;
  type: FlagType;
  environment: FlagEnvironment;
  targetType: TargetType;
  value: unknown;
  status: FlagStatus;
  rolloutPercentage: number;
  activateAt: string | null;
  deactivateAt: string | null;
  createdBy: string;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** POST /flags. `environment` defaults to GLOBAL server-side if omitted, same as `targetType`. */
export interface CreateFlagRequest {
  key: string;
  name: string;
  description?: string;
  type: FlagType;
  environment?: FlagEnvironment;
  targetType?: TargetType;
  value?: unknown;
  rolloutPercentage?: number;
  activateAt?: string;
  deactivateAt?: string;
  actor?: string;
}

/** PUT /flags/{key} - partial update, only non-null fields are applied. */
export interface UpdateFlagRequest {
  name?: string;
  description?: string;
  value?: unknown;
  rolloutPercentage?: number;
  activateAt?: string;
  deactivateAt?: string;
  actor?: string;
}

export interface FlagHistoryResponse {
  id: string;
  flagKey: string;
  versionNumber: number | null;
  action: string;
  actor: string;
  oldValue: string | null;
  newValue: string | null;
  snapshot: string | null;
  timestamp: string;
}
