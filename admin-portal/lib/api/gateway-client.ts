import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { config } from "@/lib/config";
import { authStore } from "@/store/auth-store";
import type { AuthResponse } from "@/types/auth";

/**
 * Talks to gateway-service (:8090) - the sole browser-facing REST surface.
 * It verifies the JWT, applies edge rate limiting and the per-route
 * feature-flag kill-switch, and proxies to every downstream service under
 * /api/<service>/**.
 */
export const gatewayClient = axios.create({
  baseURL: config.gatewayUrl,
  headers: { "Content-Type": "application/json" },
});

gatewayClient.interceptors.request.use((requestConfig) => {
  const { accessToken } = authStore.getState();
  if (accessToken && !requestConfig.headers.Authorization) {
    requestConfig.headers.Authorization = `Bearer ${accessToken}`;
  }
  return requestConfig;
});

// --- 401 handling: one in-flight refresh shared by every request that lands while it's running ---
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const { refreshToken } = authStore.getState();
  if (!refreshToken) throw new Error("No refresh token available");

  const response = await axios.post<AuthResponse>(
    `${config.gatewayUrl}/api/auth/refresh`,
    { refreshToken },
    { headers: { "Content-Type": "application/json" } }
  );

  authStore.getState().setTokens(response.data.accessToken, response.data.refreshToken);
  return response.data.accessToken;
}

gatewayClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;

    const isAuthEndpoint =
      originalRequest?.url?.includes("/api/auth/login") ||
      originalRequest?.url?.includes("/api/auth/refresh") ||
      originalRequest?.url?.includes("/api/auth/verify-login-otp");

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true;
      try {
        refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
        const newAccessToken = await refreshPromise;
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return gatewayClient(originalRequest);
      } catch (refreshError) {
        authStore.getState().logout();
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);
