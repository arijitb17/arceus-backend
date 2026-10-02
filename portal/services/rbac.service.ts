import { gatewayClient } from "@/lib/api/gateway-client";
import type { Role } from "@/types/auth";
import type {
  CreatePermissionRequest,
  PermissionPageResponse,
  PermissionResponse,
  RoleDefinitionResponse,
  UpdatePermissionRequest,
  UpdateRoleDetailsRequest,
  UpdateRolePermissionsRequest,
} from "@/types/rbac";

const PERMISSIONS_BASE = "/api/auth/permissions";
const ROLES_BASE = "/api/auth/roles";

export interface ListPermissionsParams {
  category?: string;
  page?: number;
  size?: number;
}

export const rbacService = {
  listPermissions: (params: ListPermissionsParams = {}) =>
    gatewayClient.get<PermissionPageResponse>(PERMISSIONS_BASE, { params }).then((r) => r.data),
  getPermission: (id: string) =>
    gatewayClient.get<PermissionResponse>(`${PERMISSIONS_BASE}/${id}`).then((r) => r.data),
  createPermission: (body: CreatePermissionRequest) =>
    gatewayClient.post<PermissionResponse>(PERMISSIONS_BASE, body).then((r) => r.data),
  updatePermission: (id: string, body: UpdatePermissionRequest) =>
    gatewayClient.put<PermissionResponse>(`${PERMISSIONS_BASE}/${id}`, body).then((r) => r.data),
  deletePermission: (id: string) => gatewayClient.delete<void>(`${PERMISSIONS_BASE}/${id}`),

  listRoles: () => gatewayClient.get<RoleDefinitionResponse[]>(ROLES_BASE).then((r) => r.data),
  getRole: (roleKey: Role) => gatewayClient.get<RoleDefinitionResponse>(`${ROLES_BASE}/${roleKey}`).then((r) => r.data),
  updateRoleDetails: (roleKey: Role, body: UpdateRoleDetailsRequest) =>
    gatewayClient.patch<RoleDefinitionResponse>(`${ROLES_BASE}/${roleKey}`, body).then((r) => r.data),
  updateRolePermissions: (roleKey: Role, body: UpdateRolePermissionsRequest) =>
    gatewayClient.put<RoleDefinitionResponse>(`${ROLES_BASE}/${roleKey}/permissions`, body).then((r) => r.data),
};
