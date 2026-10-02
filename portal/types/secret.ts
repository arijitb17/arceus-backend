/** GET/POST .../secrets - organization-service's SecretController. Never carries a decrypted value - see Secret's javadoc. */
export interface SecretResponse {
  id: string;
  secretKey: string;
  version: number;
  maskedValue: string;
  createdAt: string;
}

export interface CreateSecretRequest {
  secretKey: string;
  value: string;
}
