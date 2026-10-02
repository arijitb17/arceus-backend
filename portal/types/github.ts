/** GET /api/organizations/{orgId}/projects/{projectId}/github - organization-service's read-facing summary. */
export interface GithubConnectionSummary {
  repoFullName: string;
  defaultBranch: string;
  connectedAt: string;
}

/** GET .../deployments/github/organizations/{orgId}/projects/{projectId}/connect-url - deployment-service. */
export interface ConnectUrlResponse {
  installUrl: string;
}

export interface RepositoryOption {
  fullName: string;
  defaultBranch: string;
  isPrivate: boolean;
}

export interface ConnectRepoRequest {
  organizationId: string;
  installationId: string;
  repoFullName: string;
  defaultBranch: string;
}

export interface GithubConnectionResponse {
  id: string;
  repoFullName: string;
  defaultBranch: string;
  connectedAt: string;
}
