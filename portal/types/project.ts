export type EnvironmentType = "DEVELOPMENT" | "STAGING" | "PRODUCTION";

export const ENVIRONMENTS: EnvironmentType[] = ["DEVELOPMENT", "STAGING", "PRODUCTION"];

/** GET/POST /api/organizations/{orgId}/projects - organization-service's ProjectController. */
export interface ProjectResponse {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
  slug: string;
  description?: string;
}

export interface EnvironmentSummary {
  id: string;
  environment: EnvironmentType;
  trackedBranch: string | null;
}
