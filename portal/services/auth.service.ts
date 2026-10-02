import { gatewayClient } from "@/lib/api/gateway-client";
import type {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  MessageResponse,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  UserResponse,
  VerifyOtpRequest,
  ApiKeyResponse,
  ApiKeyCreatedResponse,
  CreateApiKeyRequest,
} from "@/types/auth";

export const authService = {
  // Step 1 of login: checks the password, triggers a login OTP email.
  login: (body: LoginRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/login", body).then((r) => r.data),

  // Step 2 of login: confirms the OTP, returns real tokens.
  verifyLoginOtp: (body: VerifyOtpRequest) =>
    gatewayClient.post<AuthResponse>("/api/auth/verify-login-otp", body).then((r) => r.data),

  register: (body: RegisterRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/register", body).then((r) => r.data),

  verifyEmail: (body: VerifyOtpRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/verify-email", body).then((r) => r.data),

  verifyPhone: (body: VerifyOtpRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/verify-phone", body).then((r) => r.data),

  forgotPassword: (body: ForgotPasswordRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/forgot-password", body).then((r) => r.data),

  verifyForgotPasswordOtp: (body: VerifyOtpRequest) =>
    gatewayClient.post<{ resetToken: string }>("/api/auth/verify-forgot-password-otp", body).then((r) => r.data),

  resetPassword: (body: ResetPasswordRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/reset-password", body).then((r) => r.data),

  changePassword: (body: ChangePasswordRequest) =>
    gatewayClient.post<MessageResponse>("/api/auth/change-password", body).then((r) => r.data),

  refresh: (body: RefreshTokenRequest) =>
    gatewayClient.post<AuthResponse>("/api/auth/refresh", body).then((r) => r.data),

  logout: (body: RefreshTokenRequest) => gatewayClient.post<void>("/api/auth/logout", body),

  me: () => gatewayClient.get<UserResponse>("/api/auth/me").then((r) => r.data),

  // --- API keys ---
  listApiKeys: () => gatewayClient.get<ApiKeyResponse[]>("/api/auth/api-keys").then((r) => r.data),

  createApiKey: (body: CreateApiKeyRequest) =>
    gatewayClient.post<ApiKeyCreatedResponse>("/api/auth/api-keys", body).then((r) => r.data),

  revokeApiKey: (id: string) => gatewayClient.delete<void>(`/api/auth/api-keys/${id}`),

  revealApiKey: (id: string) =>
    gatewayClient.get<{ key: string }>(`/api/auth/api-keys/${id}/reveal`).then((r) => r.data.key),
};
