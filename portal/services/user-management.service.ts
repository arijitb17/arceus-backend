import { gatewayClient } from "@/lib/api/gateway-client";
import type {
  SessionPageResponse,
  UpdateAccountStatusRequest,
  UpdateRolesRequest,
  UserPageResponse,
} from "@/types/user-management";
import type { AdminUserResponse } from "@/types/user-management";

// Routed through the Gateway at /api/auth/** (same prefix as the rest of Auth).
const BASE = "/api/auth/users";

export interface ListUsersParams {
  query?: string;
  page?: number;
  size?: number;
}

export const userManagementService = {
  list: (params: ListUsersParams = {}) =>
    gatewayClient.get<UserPageResponse>(BASE, { params }).then((r) => r.data),
  get: (id: string) => gatewayClient.get<AdminUserResponse>(`${BASE}/${id}`).then((r) => r.data),
  updateRoles: (id: string, body: UpdateRolesRequest) =>
    gatewayClient.patch<AdminUserResponse>(`${BASE}/${id}/roles`, body).then((r) => r.data),
  updateStatus: (id: string, body: UpdateAccountStatusRequest) =>
    gatewayClient.patch<AdminUserResponse>(`${BASE}/${id}/status`, body).then((r) => r.data),
  listSessions: (id: string, page = 0, size = 20) =>
    gatewayClient
      .get<SessionPageResponse>(`${BASE}/${id}/sessions`, { params: { page, size } })
      .then((r) => r.data),
  revokeSession: (id: string, sessionId: string) =>
    gatewayClient.delete<void>(`${BASE}/${id}/sessions/${sessionId}`),
  revokeAllSessions: (id: string) => gatewayClient.delete<void>(`${BASE}/${id}/sessions`),
};
