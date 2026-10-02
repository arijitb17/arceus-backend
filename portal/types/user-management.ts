import type { AuthProvider, Role } from "@/types/auth";

export interface AdminUserResponse {
  id: string;
  name: string;
  email: string;
  phoneNumber: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  accountEnabled: boolean;
  provider: AuthProvider;
  roles: Role[];
  createdAt: string;
  updatedAt: string;
}

export interface UserPageResponse {
  content: AdminUserResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SessionResponse {
  id: string;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
}

export interface SessionPageResponse {
  content: SessionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UpdateRolesRequest {
  roles: Role[];
}

export interface UpdateAccountStatusRequest {
  enabled: boolean;
}
