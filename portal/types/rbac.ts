import type { Role } from "@/types/auth";

export interface PermissionResponse {
  id: string;
  code: string;
  category: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PermissionPageResponse {
  content: PermissionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreatePermissionRequest {
  code: string;
  category: string;
  description?: string;
}

export interface UpdatePermissionRequest {
  category: string;
  description?: string;
}

export interface RoleDefinitionResponse {
  id: string;
  roleKey: Role;
  name: string;
  description: string | null;
  permissions: PermissionResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateRoleDetailsRequest {
  name: string;
  description?: string;
}

export interface UpdateRolePermissionsRequest {
  permissionIds: string[];
}
