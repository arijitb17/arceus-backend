export type ProjectApiKeyType = "PUBLIC" | "SECRET";

/** POST /api/auth/organizations/{orgId}/projects/{projectId}/api-keys - auth-service's ProjectApiKeyController. */
export interface CreateProjectApiKeyRequest {
  name: string;
  keyType: ProjectApiKeyType;
  environment: string;
  scopes?: string;
}

export interface ProjectApiKeyResponse {
  id: string;
  name: string;
  keyPrefix: string;
  keyType: ProjectApiKeyType;
  environment: string;
  scopes: string | null;
  revoked: boolean;
  lastUsedAt: string | null;
  createdAt: string;
}

/** Returned directly in the create response; also fetchable again later via reveal() below. */
export interface ProjectApiKeyCreatedResponse {
  id: string;
  name: string;
  keyType: ProjectApiKeyType;
  environment: string;
  key: string;
}

/** GET /api-keys/{id}/reveal - see ApiKeyCryptoService's javadoc for the trade-off this represents. */
export interface RevealedApiKeyResponse {
  key: string;
}
