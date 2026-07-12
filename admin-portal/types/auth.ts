export type Role = "ROLE_USER" | "ROLE_ADMIN";
export type AuthProvider = "LOCAL" | "GOOGLE" | "GITHUB";

/** POST /api/auth/login body. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Every step-1/step-2 flow (login, register, forgot-password) returns this until real tokens are issued. */
export interface MessageResponse {
  message: string;
}

/** POST /api/auth/verify-login-otp, /verify-forgot-password-otp (identifier), etc. body. */
export interface VerifyOtpRequest {
  identifier: string;
  otp: string;
}

/** Returned by /verify-login-otp, /refresh, /oauth2/exchange - the only place real tokens appear. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/** GET /api/auth/me */
export interface UserResponse {
  id: string;
  name: string;
  email: string;
  phoneNumber: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  provider: AuthProvider;
  roles: Role[];
}

export interface RegisterRequest {
  name: string;
  email: string;
  phoneNumber: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  resetToken: string;
  newPassword: string;
}

/** API keys - self-service, /api/auth/api-keys */
export interface ApiKeyResponse {
  id: string;
  name: string;
  keyPrefix: string;
  revoked: boolean;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface ApiKeyCreatedResponse {
  id: string;
  name: string;
  key: string;
}

export interface CreateApiKeyRequest {
  name: string;
}
